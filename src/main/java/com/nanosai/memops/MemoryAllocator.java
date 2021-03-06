package com.nanosai.memops;

import java.util.Arrays;

/**
 * The MemoryAllocator class is capable of allocating (and freeing) smaller sections of a larger byte array.
 * The underlying larger byte array is passed to the MemoryAllocator when it is instantiated, along with
 * an array of longs which is used to mark which parts of the internal byte array that is free.
 *
 * When a block (section) of the bigger array is allocated, it is allocated from the first free block that
 * has the same or larger size as the block requested. In other words, if you request a block of 1024 bytes,
 * those bytes will be allocated from the first free section that is 1024 bytes or larger.
 *
 * The length of the array of longs determines how many free blocks the MemoryAllocator can hold internally,
 * before it needs to defragment the free blocks. Defragmenting the free blocks means joining two or more
 * adjacent free blocks into a single, bigger free block.
 */
public class MemoryAllocator {

    private static long TO_AND_MASK = (long) Math.pow(2, 32)-1L;

    public  byte[] data = null;              //public because we copy data into it from MemoryBlock's and other places.
    private long[] freeBlocks = null;
    //private long[] usedBlocks = null;

    private int freeBlockCount = 0;
    //private int nextUsedBlockIndex = 0;
    private int freeBlockCountDefragLimit = 10000;

    //todo make pooled memory block count configurable
    private MemoryBlock[] pooledMemoryBlocks = new MemoryBlock[1024 * 1024]; //max 1M messages pooled.
    private int pooledMessageCount = 0;

    private IMemoryBlockFactory memoryBlockFactory = null;


    public MemoryAllocator(byte[] data, long[] freeBlocks, IMemoryBlockFactory memoryBlockFactory) {
        init(data, freeBlocks, memoryBlockFactory);
    }

    public MemoryAllocator(byte[] data, long[] freeBlocks) {
        init(data, freeBlocks, (allocator) -> { return new MemoryBlock(allocator); });
    }



    private void init(byte[] data, long[] freeBlocks, IMemoryBlockFactory factory) {
        this.data = data;
        this.freeBlocks = freeBlocks;
        this.memoryBlockFactory = factory;

        free(0, data.length);
    }

    public MemoryBlock getMemoryBlock() {
        if(this.pooledMessageCount > 0){
            this.pooledMessageCount--;
            return this.pooledMemoryBlocks[this.pooledMessageCount];
        }
        return this.memoryBlockFactory.createMemoryBlock(this);

    }




    public int capacity() {
        return this.data.length;
    }

    public int freeBlockCount() {
        return this.freeBlockCount;
    }

    public int freeCapacity() {
        int freeCapacity = 0;
        for(int i=0; i<this.freeBlockCount; i++){
            long from = this.freeBlocks[i];
            from >>=32;

            long to   = this.freeBlocks[i];
            to &= TO_AND_MASK;

            freeCapacity += (to - from);
        }

        return freeCapacity;
    }

    public int freeBlockCountDefragLimit() {
        return freeBlockCountDefragLimit;
    }

    public void freeBlockCountDefragLimit(int freeBlockCountDefragLimit) {
        this.freeBlockCountDefragLimit = freeBlockCountDefragLimit;
    }

    /**
     * This method allocates a section of the internal byte array from the first free block of that array found.
     * You should not call this method directly. Rather, obtain a MemoryBlock via getMemoryBlock() and call
     * MemoryBlock.allocate(size) on that instance.
     *
     * If a block could be allocated of the requested size, the offset into the underlying byte array of
     * that block is returned by this method. If no free block was large enough to allocate the requested
     * block, -1 is returned.
     *
     * @param blockSize The requested number of bytes to allocate
     * @return The offset into the underlying byte array of the allocated block (of the requested size),
     * or -1 if the block could not be allocated.
     */
    protected int allocate(int blockSize){

        boolean freeBlockFound = false;

        int freeBlockIndex = 0;

        while(!freeBlockFound && freeBlockIndex < this.freeBlockCount){
            long freeBlockFromIndex = this.freeBlocks[freeBlockIndex];
            freeBlockFromIndex >>=32;

            long freeBlockToIndex   = this.freeBlocks[freeBlockIndex];
            freeBlockToIndex &= TO_AND_MASK;

            if(blockSize <= (freeBlockToIndex-freeBlockFromIndex)){
                freeBlockFound = true;

                long newBlockDescriptor = freeBlockFromIndex + blockSize;
                newBlockDescriptor <<= 32;

                newBlockDescriptor += freeBlockToIndex;

                this.freeBlocks[freeBlockIndex] = newBlockDescriptor;
                return (int) freeBlockFromIndex;
            } else {
                freeBlockIndex++;
            }
        }
        return -1;
    }

    public void free(MemoryBlock memoryBlock) {
        //pool message if space
        if(this.pooledMessageCount < this.pooledMemoryBlocks.length){
            this.pooledMemoryBlocks[this.pooledMessageCount] = memoryBlock;
            this.pooledMessageCount++;
        }

        free(memoryBlock.startIndex, memoryBlock.endIndex);
    }

    public void free(int from, int to){
        long freeBlockDescriptor = from;
        freeBlockDescriptor <<= 32;

        freeBlockDescriptor += to;

        this.freeBlocks[freeBlockCount] = freeBlockDescriptor;
        freeBlockCount++;

        if(freeBlockCount == freeBlockCountDefragLimit){
            defragment();
        }
    }

    public void defragment() {
        //sort
        Arrays.sort(this.freeBlocks, 0, this.freeBlockCount);

        //merge
        int newIndex = 0;

        for(int i=0; i < freeBlockCount;){
            long from = this.freeBlocks[i];
            from >>=32;

            long to   = this.freeBlocks[i];
            to &= TO_AND_MASK;

            int nextIndex  = i + 1;

            long nextFrom = this.freeBlocks[nextIndex];
            nextFrom >>=32;

            long nextTo   = this.freeBlocks[nextIndex];
            nextTo &= TO_AND_MASK;

            while(to == nextFrom ){
                to = nextTo;      //todo this can be moved to after while loop?
                nextIndex++;
                if(nextIndex == this.freeBlockCount){
                    break;
                }

                nextFrom   = this.freeBlocks[nextIndex];
                nextFrom >>=32;

                nextTo     = this.freeBlocks[nextIndex];
                nextTo    &= TO_AND_MASK;
            }

            i = nextIndex;

            long newBlockDescriptor = from;
            newBlockDescriptor <<= 32;

            newBlockDescriptor += to;

            this.freeBlocks[newIndex] = newBlockDescriptor;
            newIndex++;
        }
        this.freeBlockCount = newIndex;
    }

}
