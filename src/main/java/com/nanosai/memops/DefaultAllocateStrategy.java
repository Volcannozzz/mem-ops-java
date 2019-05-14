package com.nanosai.memops;

import com.nanosai.memops.util.Mask;

/**
 * @author Volcanno
 */
public class DefaultAllocateStrategy implements AllocateStrategy {

    @Override
    public int allocate(int blockSize, long[] freeBlocks, int freeBlockCount) {

        int freeBlockIndex = 0;

        while (freeBlockIndex < freeBlockCount) {
            long freeBlockFromIndex = Mask.getHead(freeBlocks[freeBlockIndex]);
            long freeBlockToIndex = Mask.getTail(freeBlocks[freeBlockIndex]);
            if (blockSize <= (freeBlockToIndex - freeBlockFromIndex)) {
                long head = freeBlockFromIndex + blockSize;
                long newBlockDescriptor = Mask.getDescriptor(head, freeBlockToIndex);
                freeBlocks[freeBlockIndex] = newBlockDescriptor;
                return (int) freeBlockFromIndex;
            } else {
                freeBlockIndex++;
            }
        }
        return -1;
    }
}
