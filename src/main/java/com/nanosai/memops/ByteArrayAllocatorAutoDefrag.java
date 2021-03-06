package com.nanosai.memops;

import com.nanosai.memops.util.Mask;

/**
 * The ByteArrayAllocatorAutoDefrag class is capable of allocating (and freeing) smaller sections of a larger byte array.
 * The underlying larger byte array is passed to the ByteArrayAllocatorAutoDefrag when it is instantiated.
 * <p>
 * When a block (section) of the bigger array is allocated, it is allocated from the first free block that
 * has the same or larger size as the block requested. In other words, if you request a block of 1024 bytes,
 * those bytes will be allocated from the first free section that is 1024 bytes or larger.
 */
public class ByteArrayAllocatorAutoDefrag {

    private static int FREE_BLOCK_ARRAY_SIZE_INCREMENT = 16;

    private byte[] data = null;
    private long[] freeBlocks = new long[FREE_BLOCK_ARRAY_SIZE_INCREMENT];

    private int freeBlockCount = 0;

    private AllocateStrategy allocateStrategy;

    public ByteArrayAllocatorAutoDefrag(byte[] data) {
        init(data);
    }

    public ByteArrayAllocatorAutoDefrag setAllocateStrategy(AllocateStrategy strategy) {
        this.allocateStrategy = strategy;
        return this;
    }

    private void init(byte[] data) {
        this.data = data;
        this.allocateStrategy = new DefaultAllocateStrategy();
        free(0, data.length);
    }

    public byte[] getData() {
        return this.data;
    }

    public int capacity() {
        return this.data.length;
    }

    public int freeBlockCount() {
        return this.freeBlockCount;
    }

    public int freeCapacity() {
        int freeCapacity = 0;
        for (int i = 0; i < this.freeBlockCount; i++) {
            long from = this.freeBlocks[i];
            from >>= 32;

            long to = this.freeBlocks[i];
            to &= Mask.TO_AND_MASK;

            freeCapacity += (to - from);
        }

        return freeCapacity;
    }

    /**
     * This method allocates a section of the internal byte array from the first free block of that array found.
     * You should not call this method directly. Rather, obtain a MemoryBlock via getMemoryBlock() and call
     * MemoryBlock.allocate(size) on that instance.
     * <p>
     * If a block could be allocated of the requested size, the offset into the underlying byte array of
     * that block is returned by this method. If no free block was large enough to allocate the requested
     * block, -1 is returned.
     *
     * @param blockSize The requested number of bytes to allocate
     * @return The offset into the underlying byte array of the allocated block (of the requested size),
     * or -1 if the block could not be allocated.
     */
    protected int allocate(int blockSize) {
        return allocateStrategy.allocate(blockSize, this.freeBlocks, this.freeBlockCount);
    }


    public void free(long from, long to) {

        long freeBlockDescriptor = ((from << 32) + to);
        for (int i = 0; i < this.freeBlockCount; i++) {
            if (freeBlockDescriptor < this.freeBlocks[i]) {
                //insert the free block here - at index i
                boolean mergeWithPreviousBlock = i > 0 && (from == (int) (this.freeBlocks[i - 1] & Mask.TO_AND_MASK));
                boolean mergeWithNextBlock = (to == (int) (this.freeBlocks[i] >> 32));

                if (mergeWithPreviousBlock && mergeWithNextBlock) {
                    this.freeBlocks[i - 1] = (this.freeBlocks[i - 1] & Mask.FROM_AND_MASK) | (this.freeBlocks[i] & Mask.TO_AND_MASK);
                    int length = this.freeBlockCount - i - 1;
                    System.arraycopy(this.freeBlocks, i + 1, this.freeBlocks, i, this.freeBlockCount - i - 1);
                    this.freeBlockCount--;
                    return;
                }
                if (mergeWithPreviousBlock) {
                    this.freeBlocks[i - 1] = (this.freeBlocks[i - 1] & Mask.FROM_AND_MASK) | to;
                    return;
                }
                if (mergeWithNextBlock) {
                    this.freeBlocks[i] = (from << 32) | (this.freeBlocks[i] & Mask.TO_AND_MASK);
                    return;
                }

                //new free block is not adjacent to either previous nor next block, so insert it here.
                int length = this.freeBlockCount - i;
                System.arraycopy(this.freeBlocks, i, this.freeBlocks, i + 1, length);
                this.freeBlocks[i] = freeBlockDescriptor;
                this.freeBlockCount++;
                return;
            }
        }

        //no place found to insert the free block, so append it at the end instead.
        if (this.freeBlockCount >= this.freeBlocks.length) {
            //expand array
            long[] newFreeBlocks = new long[this.freeBlocks.length + FREE_BLOCK_ARRAY_SIZE_INCREMENT];
            System.arraycopy(this.freeBlocks, 0, newFreeBlocks, 0, this.freeBlocks.length);
            this.freeBlocks = newFreeBlocks;
        }
        this.freeBlocks[freeBlockCount] = freeBlockDescriptor;
        freeBlockCount++;
    }


}
