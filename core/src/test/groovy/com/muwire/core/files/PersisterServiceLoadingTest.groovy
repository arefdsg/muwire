package com.muwire.core.files

import org.junit.Before
import org.junit.Test

import com.muwire.core.Destinations
import com.muwire.core.DownloadedFile
import com.muwire.core.EventBus
import com.muwire.core.InfoHash
import com.muwire.core.SharedFile
import com.muwire.core.util.DataUtil

import groovy.json.JsonOutput
import groovy.util.GroovyTestCase
import net.i2p.data.Base32
import net.i2p.data.Base64

class PersisterServiceLoadingTest {

    class Listener {
        def publishedFiles = []
        def onFileLoadedEvent(FileLoadedEvent e) {
            publishedFiles.add(e.loadedFile)
        }
    }

    EventBus eventBus
    Listener listener
    File sharedDir
    File sharedFile1, sharedFile2

    @Before
    void setup() {
        eventBus = new EventBus()
        listener = new Listener()
        eventBus.register(FileLoadedEvent.class, listener)

        sharedDir = new File("sharedDir")
        sharedDir.mkdir()
        sharedDir.deleteOnExit()

        sharedFile1 = new File(sharedDir,"file1")
        sharedFile1.deleteOnExit()

        sharedFile2 = new File(sharedDir,"file2")
        sharedFile2.deleteOnExit()
    }

    private void writeToSharedFile(File file, int size) {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write new byte[size]
        fos.close()
    }

    private File initPersisted() {
        File persisted = new File("persisted")
        if (persisted.exists())
            persisted.delete()
        persisted.deleteOnExit()
        persisted
    }

    @Test
    void test1SharedFile1Piece() {
        writeToSharedFile(sharedFile1, 1)
        FileHasher fh = new FileHasher()
        InfoHash ih1 = fh.hashFile(sharedFile1)

        def json = [:]
        json.file = getSharedFileJsonName(sharedFile1)
        json.length = 1
        json.infoHash = Base64.encode(ih1.getRoot())
        json.hashList = [Base64.encode(ih1.getHashList())]

        json = JsonOutput.toJson(json)

        File persisted = initPersisted()
        persisted.write json

        PersisterService ps = new PersisterService(persisted, eventBus, 100, null)
        ps.onUILoadedEvent(null)
        ps.onWatchedDirectoriesLoadedEvent(null)
        Thread.sleep(2000)

        assert listener.publishedFiles.size() == 1
        def loadedFile = listener.publishedFiles[0]
        assert loadedFile != null
        assert loadedFile.file == sharedFile1.getCanonicalFile()
        assert loadedFile.root == ih1.getRoot()
    }

    private static String getSharedFileJsonName(File sharedFile) {
        def encoded = DataUtil.encodei18nString(sharedFile.getCanonicalFile().toString())
        Base64.encode(encoded)
    }

    @Test
    public void test1SharedFile2Pieces() {
        writeToSharedFile(sharedFile1, (0x1 << 18) + 1)
        FileHasher fh = new FileHasher()
        InfoHash ih1 = fh.hashFile(sharedFile1)

        assert ih1.getHashList().length == 96

        def json = [:]
        json.file = getSharedFileJsonName(sharedFile1)
        json.length = sharedFile1.length()
        json.infoHash = Base64.encode ih1.getRoot()

        byte [] tmp = new byte[32]
        System.arraycopy(ih1.getHashList(), 0, tmp, 0, 32)
        String hash1 = Base64.encode(tmp)
        System.arraycopy(ih1.getHashList(), 32, tmp, 0, 32)
        String hash2 = Base64.encode(tmp)
        System.arraycopy(ih1.getHashList(), 64, tmp, 0, 32)
        String hash3 = Base64.encode(tmp)
        json.hashList = [hash1, hash2, hash3]

        json = JsonOutput.toJson(json)

        File persisted = initPersisted()
        persisted.write json

        PersisterService ps = new PersisterService(persisted, eventBus, 100, null)
        ps.onUILoadedEvent(null)
        ps.onWatchedDirectoriesLoadedEvent(null)
        Thread.sleep(2000)

        assert listener.publishedFiles.size() == 1
        def loadedFile = listener.publishedFiles[0]
        assert loadedFile != null
        assert loadedFile.file == sharedFile1.getCanonicalFile()
        assert loadedFile.root == ih1.getRoot()
    }

    @Test
    void test2SharedFiles() {
        writeToSharedFile(sharedFile1, 1)
        writeToSharedFile(sharedFile2, 2)
        FileHasher fh = new FileHasher()
        InfoHash ih1 = fh.hashFile(sharedFile1)
        InfoHash ih2 = fh.hashFile(sharedFile2)

        assert ih1 != ih2

        File persisted = initPersisted()

        def json1 = [:]
        json1.file = getSharedFileJsonName(sharedFile1)
        json1.length = 1
        json1.infoHash = Base64.encode(ih1.getRoot())
        json1.hashList = [Base64.encode(ih1.getHashList())]

        json1 = JsonOutput.toJson(json1)

        def json2 = [:]
        json2.file = getSharedFileJsonName(sharedFile2)
        json2.length = 2
        json2.infoHash = Base64.encode(ih2.getRoot())
        json2.hashList = [Base64.encode(ih2.getHashList())]

        json2 = JsonOutput.toJson(json2)

        persisted.append "$json1\n"
        persisted.append "$json2\n"

        PersisterService ps = new PersisterService(persisted, eventBus, 100, null)
        ps.onUILoadedEvent(null)
        ps.onWatchedDirectoriesLoadedEvent(null)
        Thread.sleep(2000)

        assert listener.publishedFiles.size() == 2
        def loadedFile1 = listener.publishedFiles[0]
        assert loadedFile1.file == sharedFile1.getCanonicalFile()
        assert loadedFile1.root == ih1.getRoot()
        def loadedFile2 = listener.publishedFiles[1]
        assert loadedFile2.file == sharedFile2.getCanonicalFile()
        assert loadedFile2.root == ih2.getRoot()
    }

    @Test
    void testDownloadedFile() {
        writeToSharedFile(sharedFile1, 1)
        FileHasher fh = new FileHasher()
        InfoHash ih1 = fh.hashFile(sharedFile1)

        File persisted = initPersisted()

        Destinations dests = new Destinations()
        def json1 = [:]
        json1.file = getSharedFileJsonName(sharedFile1)
        json1.length = 1
        json1.infoHash = Base64.encode(ih1.getRoot())
        json1.hashList = [Base64.encode(ih1.getHashList())]
        json1.sources = [ dests.dest1.toBase64(), dests.dest2.toBase64()]

        json1 = JsonOutput.toJson(json1)
        persisted.write json1

        PersisterService ps = new PersisterService(persisted, eventBus, 100, null)
        ps.onUILoadedEvent(null)
        ps.onWatchedDirectoriesLoadedEvent(null)
        Thread.sleep(2000)

        assert listener.publishedFiles.size() == 1
        def loadedFile1 = listener.publishedFiles[0]
        assert loadedFile1 instanceof DownloadedFile
        assert loadedFile1.sources.size() == 2
        assert loadedFile1.sources.contains(dests.dest1)
        assert loadedFile1.sources.contains(dests.dest2)

    }
}
