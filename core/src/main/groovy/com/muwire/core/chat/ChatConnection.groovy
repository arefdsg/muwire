package com.muwire.core.chat

import com.muwire.core.profile.MWProfileHeader

import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

import com.muwire.core.Constants
import com.muwire.core.EventBus
import com.muwire.core.MuWireSettings
import com.muwire.core.Persona
import com.muwire.core.connection.Endpoint
import com.muwire.core.trust.TrustLevel
import com.muwire.core.trust.TrustService

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log
import net.i2p.crypto.DSAEngine
import net.i2p.data.Base64
import net.i2p.data.Signature
import net.i2p.data.SigningPrivateKey

@Log
class ChatConnection implements ChatLink {
    
    private static final long PING_INTERVAL = 20000
    private static final long MAX_CHAT_AGE = 5 * 60 * 1000
    
    private final EventBus eventBus
    private final Endpoint endpoint
    private final Persona persona
    private final MWProfileHeader profileHeader
    private final boolean incoming
    private final TrustService trustService
    private final MuWireSettings settings
    private final int version
    
    private final AtomicBoolean running = new AtomicBoolean()
    private final BlockingQueue messages = new LinkedBlockingQueue()
    private final Thread reader, writer
    private final LinkedList<Long> timestamps = new LinkedList<>()
    private final BlockingQueue incomingEvents = new LinkedBlockingQueue()
    
    private final DataInputStream dis
    private final DataOutputStream dos
    
    private final JsonSlurper slurper = new JsonSlurper()
    
    private volatile long lastPingSentTime
    
    ChatConnection(EventBus eventBus, Endpoint endpoint, Persona persona, MWProfileHeader profileHeader, 
                   boolean incoming, TrustService trustService, MuWireSettings settings, int version) {
        this.eventBus = eventBus
        this.endpoint = endpoint
        this.persona = persona
        this.profileHeader = profileHeader
        this.incoming = incoming
        this.trustService = trustService
        this.settings = settings
        this.version = version
        
        this.dis = new DataInputStream(endpoint.getInputStream())
        this.dos = new DataOutputStream(endpoint.getOutputStream())

        this.reader = new Thread({readLoop()} as Runnable)
        this.reader.setName("reader-${persona.getHumanReadableName()}")
        this.reader.setDaemon(true)
        
        this.writer = new Thread({writeLoop()} as Runnable)
        this.writer.setName("writer-${persona.getHumanReadableName()}")
        this.writer.setDaemon(true)        
    }
    
    void start() {
        if (!running.compareAndSet(false, true)) {
            log.log(Level.WARNING,"${persona.getHumanReadableName()} already running", new Exception())
            return
        }
        reader.start()
        writer.start()
    }
    
    @Override
    public boolean isUp() {
        running.get()
    }
    
    @Override
    public Persona getPersona() {
        persona
    }
    
    @Override
    public MWProfileHeader getProfileHeader() {
        profileHeader
    }
    
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            log.log(Level.WARNING,"${persona.getHumanReadableName()} already closed", new Exception())
            return
        }
        log.info("Closing "+persona.getHumanReadableName())
        reader.interrupt()
        writer.interrupt()
        endpoint.close()
        eventBus.publish(new ChatDisconnectionEvent(persona : persona))
    }
    
    private void readLoop() {
        try {
            while(running.get())
                read()
        } catch( InterruptedException | SocketTimeoutException ignored) {
        } catch (Exception e) {
          log.log(Level.WARNING,"unhandled exception in reader", e)  
        } finally {
            try {endpoint.getOutputStream().close()} catch (IOException ignore) {}
            close()
        }
    }
    
    private void writeLoop() {
        try {
            while(running.get()) {
                def message = messages.take()
                write(message)
            }
        } catch (InterruptedException ignore) {
        } catch (Exception e) {
            log.log(Level.WARNING,"unhandled exception in writer",e)
        } finally {
            try {endpoint.getOutputStream().close()} catch (IOException ignore) {}
            close()
        }
    }
    
    private void read() {
        int length = version == 1 ? dis.readUnsignedShort() : dis.readInt()
        if (length > (0x1 << 19))
            throw new Exception("chat message too big $length")
        byte [] payload = new byte[length]
        dis.readFully(payload)
        def json = slurper.parse(payload)
        if (json.type == null)
            throw new Exception("missing json type")
        switch(json.type) {
            case "Ping" : break // just ignore
            case "Chat" : handleChat(json); break
            case "Leave": handleLeave(json); break
            default :
                throw new Exception("unknown json type ${json.type}")
        }
    }
    
    private void write(Object message) {
        byte [] payload = JsonOutput.toJson(message).bytes
        dos.with {
            if (version == 1) 
                writeShort(payload.length) 
            else
                writeInt(payload.length)
            write(payload)
            flush()
        }
    }
    
    void sendPing() {
        long now = System.currentTimeMillis()
        if (now - lastPingSentTime < PING_INTERVAL)
            return
        def ping = [:]
        ping.type = "Ping"
        ping.version = 1
        messages.put(ping)
        lastPingSentTime = now
    }
    
    private void handleChat(def json) {
        UUID uuid = UUID.fromString(json.uuid)
        Persona host = fromString(json.host)
        Persona sender = fromString(json.sender)
        long chatTime = json.chatTime
        String room = json.room
        String payload = json.payload
        byte [] sig = Base64.decode(json.sig)
        
        if (!verify(uuid,host,sender,chatTime,room,payload,sig)) {
            log.warning("chat didn't verify")
            return
        }
        if (incoming) {
            if (sender.destination != endpoint.destination) {
                log.warning("Sender destination mismatch, dropping message")
                return
            }
        } else {
            if (host.destination != endpoint.destination) {
                log.warning("Host destination mismatch, dropping message")
                return
            }
        }
        if (System.currentTimeMillis() - chatTime > MAX_CHAT_AGE) {
            log.warning("Chat too old, dropping")
            return
        }
        switch(trustService.getLevel(sender.destination)) {
            case TrustLevel.TRUSTED : break
            case TrustLevel.NEUTRAL :
                if (!settings.allowUntrusted) 
                    return
                else
                    break
            case TrustLevel.DISTRUSTED :
                return
        }
        def event = new ChatMessageEvent( uuid : uuid, payload : payload, sender : sender,
            host : host, link: this, room : room, chatTime : chatTime, sig : sig)
        eventBus.publish(event)
        if (!incoming)
            incomingEvents.put(event)
    }
    
    private void handleLeave(def json) {
        Persona leaver = fromString(json.persona)
        eventBus.publish(new UserDisconnectedEvent(user : leaver, host : persona))
        incomingEvents.put(leaver)
    }
    
    private static Persona fromString(String base64) {
        new Persona(new ByteArrayInputStream(Base64.decode(base64)))
    }
    
    private static boolean verify(UUID uuid, Persona host, Persona sender, long chatTime, 
        String room, String payload, byte []sig) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        DataOutputStream daos = new DataOutputStream(baos)
        daos.write(uuid.toString().bytes)
        host.write(daos)
        sender.write(daos)
        daos.writeLong(chatTime)
        daos.write(room.getBytes(StandardCharsets.UTF_8))
        daos.write(payload.getBytes(StandardCharsets.UTF_8))
        daos.close()
        byte [] signed = baos.toByteArray()
        def spk = sender.destination.getSigningPublicKey()
        def signature = new Signature(spk.getType(), sig)
        DSAEngine.getInstance().verifySignature(signature, signed, spk)
    }
    
    public static byte[] sign(UUID uuid, long chatTime, String room, String words, Persona sender, Persona host, SigningPrivateKey spk) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        DataOutputStream daos = new DataOutputStream(baos)
        daos.with {
            write(uuid.toString().bytes)
            host.write(daos)
            sender.write(daos)
            writeLong(chatTime)
            write(room.getBytes(StandardCharsets.UTF_8))
            write(words.getBytes(StandardCharsets.UTF_8))
            close()
        }
        byte [] payload = baos.toByteArray()
        Signature sig = DSAEngine.getInstance().sign(payload, spk)
        sig.getData()
    }
    
    void sendChat(ChatMessageEvent e) {
        def chat = [:]
        chat.type = "Chat"
        chat.uuid = e.uuid.toString()
        chat.host = e.host.toBase64()
        chat.sender = e.sender.toBase64()
        chat.chatTime = e.chatTime
        chat.room = e.room
        chat.payload = e.payload
        chat.sig = Base64.encode(e.sig)
        messages.put(chat)
    }
    
    void sendLeave(Persona p) {
        def leave = [:]
        leave.type = "Leave"
        leave.persona = p.toBase64()
        messages.put(leave)
    }
    
    public Object nextEvent() throws InterruptedException {
        incomingEvents.take()
    }
}
