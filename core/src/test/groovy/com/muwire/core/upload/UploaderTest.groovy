package com.muwire.core.upload


import java.nio.charset.StandardCharsets

import org.junit.After
import org.junit.Before
import org.junit.Test

import com.muwire.core.InfoHash
import com.muwire.core.connection.Endpoint
import com.muwire.core.download.Pieces
import com.muwire.core.files.FileHasher
import com.muwire.core.mesh.Mesh

class UploaderTest {

    Endpoint endpoint
    File file
    Thread uploadThread

    InputStream is
    OutputStream os

    ContentRequest request
    Uploader uploader

    byte[] inFile

    @Before
    public void setup() {
        file?.delete()
        file = File.createTempFile("uploadTest", "dat")
        file.deleteOnExit()
        is = new PipedInputStream(0x1 << 15)
        os = new PipedOutputStream(is)
        endpoint = new Endpoint(null, is, os, null)
    }

    @After
    public void teardown() {
        file?.delete()
        uploadThread?.interrupt()
        Thread.sleep(50)
    }

    private void fillFile(int length) {
        byte [] data = new byte[length]
        def random = new Random()
        random.nextBytes(data)
        def fos = new FileOutputStream(file)
        fos.write(data)
        fos.close()
        inFile = data
    }

    private void startUpload() {
        def hasher = new FileHasher()
        InfoHash infoHash = hasher.hashFile(file)
        Pieces pieces = new Pieces(FileHasher.getPieceSize(file.length()))
        for (int i = 0; i < pieces.nPieces; i++)
            pieces.markDownloaded(i)
        Mesh mesh = new Mesh(infoHash, pieces)
        uploader = new ContentUploader(file, request, endpoint, mesh, FileHasher.getPieceSize(file.length()))
        uploadThread = new Thread(uploader.respond() as Runnable)
        uploadThread.setDaemon(true)
        uploadThread.start()
    }

    private String readUntilRN() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        while(true) {
            byte read = is.read()
            if (read == -1)
                throw new IOException()
            if (read != '\r') {
                baos.write(read)
                continue
            }
            assert is.read() == '\n'
            break
        }
        new String(baos.toByteArray(), StandardCharsets.US_ASCII)
    }

    @Test
    public void testSmallFile() {
        fillFile(20)
        request = new ContentRequest(range : new Range(0,19))
        startUpload()
        assert "200 OK" == readUntilRN()
        assert "Content-Range: 0-19" == readUntilRN()
        assert readUntilRN().startsWith("X-Have")
        assert readUntilRN().startsWith("Head")
        assert "" == readUntilRN()

        byte [] data = new byte[20]
        DataInputStream dis = new DataInputStream(is)
        dis.readFully(data)
        assert inFile == data
    }

    @Test
    public void testRequestMiddle() {
        fillFile(20)
        request = new ContentRequest(range : new Range(5,15))
        startUpload()
        assert "200 OK" == readUntilRN()
        assert "Content-Range: 5-15" == readUntilRN()
        assert readUntilRN().startsWith("X-Have")
        assert readUntilRN().startsWith("Head")
        assert "" == readUntilRN()

        byte [] data = new byte[11]
        DataInputStream dis = new DataInputStream(is)
        dis.readFully(data)
        for (int i = 0; i < data.length; i++)
            assert inFile[i+5] == data[i]
    }

    @Test
    public void testOutOfRange() {
        fillFile(20)
        request = new ContentRequest(range : new Range(0,20))
        startUpload()
        assert "416 Range Not Satisfiable" == readUntilRN()
        assert readUntilRN().startsWith("X-Have")
        assert "" == readUntilRN()
    }

    @Test
    public void testLargeFile() {
        final int length = 0x1 << 14
        fillFile(length)
        request = new ContentRequest(range : new Range(0, length - 1))
        startUpload()
        readUntilRN()
        readUntilRN()
        readUntilRN()
        readUntilRN()
        readUntilRN()

        byte [] data = new byte[length]
        DataInputStream dis = new DataInputStream(is)
        dis.readFully(data)
        assert data == inFile
    }
}
