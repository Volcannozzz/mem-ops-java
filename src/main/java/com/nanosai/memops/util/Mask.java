package com.nanosai.memops.util;

/**
 * @author Volcanno
 */
public class Mask {

    public static long FROM_AND_MASK = (long) 0xFFFFFFFF00000000L;
    public static long TO_AND_MASK = (long) 0x00000000FFFFFFFFL;


    public static long getTail(long value) {
        return value & TO_AND_MASK;
    }

    public static long getHead(long value) {
        return value >> 32;
    }

    public static long getDescriptor(long head, long tail) {
        return head << 32 + tail;
    }

}
