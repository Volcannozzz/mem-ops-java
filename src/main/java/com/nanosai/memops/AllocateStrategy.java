package com.nanosai.memops;

/**
 * @author Volcanno
 */
public interface AllocateStrategy {

    /**
     * @param blockSize      requested blockSize
     * @param freeBlocks     free Block descriptor set
     * @param freeBlockCount free block count
     * @return The offset into the underlying byte array of the allocated block (of the requested size),
     * or -1 if the block could not be allocated.
     */
    int allocate(int blockSize, long[] freeBlocks, int freeBlockCount);
}
