package com.muwire.core.util

import static org.junit.Assert.fail

import org.junit.Test

class DataUtilTest {


    private static void usVal(int value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        DataUtil.writeUnsignedShort(value, baos)
        def is = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))
        assert is.readUnsignedShort() == value
    }
    @Test
    void testUnsignedShort() {
        usVal(0)
        usVal(20)
        usVal(Short.MAX_VALUE)
        usVal(Short.MAX_VALUE + 1)
        usVal(0xFFFF)

        try {
            usVal(0xFFFF + 1)
            fail()
        } catch (IllegalArgumentException expected) {}
    }

    private static header(int value) {
        byte [] header = new byte[3]
        DataUtil.packHeader(value, header)
        assert value == DataUtil.readLength(header)
    }
    
    private static binaryHeader(int value) {
        byte [] header = new byte[3]
        DataUtil.packHeader(value, header)
        header[0] |= (byte)0x80
        assert value == DataUtil.readLength(header)
    }

    @Test
    void testHeader() {
        header(0)
        header(1)
        header(556)
        header(8 * 1024 * 1024 - 1)
        try {
            header(8 * 1024 *  1024)
            fail()
        } catch (IllegalArgumentException expected) {}
    }
    
    @Test
    void testBinaryHeader() {
        binaryHeader(0)
        binaryHeader(1)
        binaryHeader(556)
        binaryHeader(8 * 1024 * 1024 - 1)
        try {
            binaryHeader(8 * 1024 *  1024)
            fail()
        } catch (IllegalArgumentException expected) {}
        
    }
}
