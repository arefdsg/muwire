package com.muwire.core.download;

import net.i2p.data.Base64

import com.muwire.core.Constants
import com.muwire.core.EventBus
import com.muwire.core.InfoHash
import com.muwire.core.Persona
import com.muwire.core.connection.Endpoint
import com.muwire.core.util.DataUtil
import org.h2.engine.Session

import static com.muwire.core.util.DataUtil.readTillRN

import groovy.util.logging.Log

import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

@Log
class DownloadSession {

    private final EventBus eventBus
    private final String meB64
    private final Pieces pieces
    private final InfoHash infoHash
    private final Endpoint endpoint
    private final File file
    private final int pieceSize
    private final long fileLength
    private final Set<Integer> available
    private final MessageDigest digest
    private final boolean browse, feed, chat, message

    private final AtomicLong dataSinceLastRead

    private MappedByteBuffer mapped
    private boolean unclaim = true
    private boolean steal
    int piece, position
    private long pieceStart, start, end
    boolean supportsHead

    DownloadSession(EventBus eventBus, String meB64, Pieces pieces, InfoHash infoHash, Endpoint endpoint, File file,
        int pieceSize, long fileLength, Set<Integer> available, AtomicLong dataSinceLastRead,
        boolean browse, boolean feed, boolean chat, boolean message) {
        this.eventBus = eventBus
        this.meB64 = meB64
        this.pieces = pieces
        this.endpoint = endpoint
        this.infoHash = infoHash
        this.file = file
        this.pieceSize = pieceSize
        this.fileLength = fileLength
        this.available = available
        this.dataSinceLastRead = dataSinceLastRead
        this.browse = browse
        this.feed = feed
        this.chat = chat
        this.message = message
        try {
            digest = MessageDigest.getInstance("SHA-256")
        } catch (NoSuchAlgorithmException impossible) {
            digest = null
            System.exit(1)
        }
    }
    
    /**
     * @return if the request will proceed.  The only time it may not
     * is if all the pieces have been claimed by other sessions.
     * @throws IOException
     */
    public boolean sendRequest() throws IOException {
        OutputStream os = endpoint.getOutputStream()

        int[] pieceAndPosition
        if (available.isEmpty())
            pieceAndPosition = pieces.claim()
        else
            pieceAndPosition = pieces.claim(new HashSet<>(available))
        if (pieceAndPosition == null)
            return false
            
        piece = pieceAndPosition[0]
        position = pieceAndPosition[1]
        steal = pieceAndPosition[2] == 1
        
        log.info("will request piece $piece from position $position steal $steal")
        
        pieceStart = piece * ((long)pieceSize)
        end = Math.min(fileLength, pieceStart + pieceSize) - 1
        start = pieceStart + position

        String root = Base64.encode(infoHash.getRoot())
        
        boolean headersSent = false
        try {
            os.write("GET $root\r\n".getBytes(StandardCharsets.US_ASCII))
            os.write("Range: $start-$end\r\n".getBytes(StandardCharsets.US_ASCII))
            os.write("X-Persona: $meB64\r\n".getBytes(StandardCharsets.US_ASCII))
            SessionSupport.writeInteractionHeaders(os, browse, chat, feed, message)
            SessionSupport.writeXHave(os, pieces)
            headersSent = true
            return true
        } finally {
            if (!headersSent && !steal) 
                pieces.unclaim(piece)
        }
    }

    /**
     * @return true if the response was consumed, false if it cannot be satisfied.
     * @throws IOException
     */
    public boolean consumeResponse() throws IOException {
        OutputStream os = endpoint.getOutputStream()
        InputStream is = endpoint.getInputStream()

        try {
            String codeString = readTillRN(is)
            int space = codeString.indexOf(' ')
            if (space > 0)
                codeString = codeString.substring(0, space)

            int code = Integer.parseInt(codeString.trim())

            if (code == 404) {
                log.warning("file not found")
                endpoint.close()
                return false
            }

            if (!(code == 200 || code == 416)) {
                log.warning("unknown code $code")
                endpoint.close()
                return false
            }

            // parse all headers
            Map<String,String> headers = DataUtil.readAllHeaders(is)
            
            if (headers.containsKey("Head") && Boolean.parseBoolean(headers["Head"]))
                supportsHead = true

            // prase X-Alt if present
            if (headers.containsKey("X-Alt")) {
                headers["X-Alt"].split(",").each {
                    if (it.length() > 0) {
                        byte [] raw = Base64.decode(it)
                        Persona source = new Persona(new ByteArrayInputStream(raw))
                        eventBus.publish(new SourceDiscoveredEvent(infoHash : infoHash, source : source))
                    }
                }
            }

            // parse X-Have if present
            if (headers.containsKey("X-Have")) {
                DataUtil.decodeXHave(headers["X-Have"]).each {
                    if (it >= pieces.nPieces)
                        throw new IOException("Invalid X-Have header, available piece $it/$pieces.nPieces")
                    available.add(it)
                }
                if (!available.contains(piece))
                    return true // try again next time
            } else {
                if (code != 200)
                    throw new IOException("Code $code but no X-Have")
                available.clear()
            }

            if (code != 200)
                return true

            String range = headers["Content-Range"]
            if (range == null)
                throw new IOException("Code 200 but no Content-Range")

            def group = (range =~ /^(\d+)-(\d+)$/)
            if (group.size() != 1)
                throw new IOException("invalid Content-Range header $range")

            long receivedStart = Long.parseLong(group[0][1])
            long receivedEnd = Long.parseLong(group[0][2])

            if (receivedStart != start || receivedEnd != end) {
                log.warning("We don't support mismatching ranges yet")
                endpoint.close()
                return false
            }

            // start the download
            FileChannel channel
            try {
                channel = Files.newByteChannel(file.toPath(), EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE,
                        StandardOpenOption.SPARSE, StandardOpenOption.CREATE)) 
                mapped = channel.map(FileChannel.MapMode.READ_WRITE, pieceStart, end - pieceStart + 1)
                mapped.position(position)

                byte[] tmp = new byte[0x1 << 13]
                DataInputStream dis = new DataInputStream(is)
                while(mapped.hasRemaining()) {
                    if (mapped.remaining() < tmp.length)
                        tmp = new byte[mapped.remaining()]
                    dis.readFully(tmp)
                    synchronized(this) {
                        mapped.put(tmp)
                        dataSinceLastRead.addAndGet(tmp.length)
                        pieces.markPartial(piece, mapped.position())
                    }
                }

                mapped.clear()
                digest.update(mapped)
                byte [] hash = digest.digest()
                byte [] expected = new byte[32]
                System.arraycopy(infoHash.getHashList(), piece * 32, expected, 0, 32)
                if (hash != expected) {
                    pieces.markPartial(piece, 0)
                    throw new BadHashException("bad hash on piece $piece")
                }
                
                eventBus.publish(new SourceVerifiedEvent(infoHash : infoHash, source : endpoint.destination))
            } finally {
                try { channel?.close() } catch (IOException ignore) {}
                DataUtil.tryUnmap(mapped)
            }
            pieces.markDownloaded(piece)
            unclaim = false
        } finally {
            if (unclaim && !steal) 
                pieces.unclaim(piece)
        }
        return true
    }

    synchronized int positionInPiece() {
        if (mapped == null)
            return 0
        mapped.position()
    }
}
