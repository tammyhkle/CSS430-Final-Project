
public class SuperBlock {
   
   private final int defaultInodeBlocks = 64;
   public int totalBlocks;       // the number of disk blocks
   public int totalInodes;       // the number of inodes
   public int freeList;          // the block number of the free list's head

   public SuperBlock(int diskSize) {
      // read the superblock from disk.  
      byte [] superBlock = new byte[Disk.blockSize];
      SysLib.rawread(0, superBlock);
      totalBlocks = SysLib.bytes2int(superBlock, 0);
		totalInodes = SysLib.bytes2int(superBlock, 4);
		freeList = SysLib.bytes2int(superBlock, 8);

      // check disk contents are valid.
      if(totalBlocks == diskSize && totalInodes > 0 && freeList >= 2) {
         return;
      } else {       // if invalid, call format( ).
         totalBlocks = diskSize;
         format(defaultInodeBlocks);
      }
   }

   // write back in-memory superblock to disk: SysLib.rawwrite( 0, superblock ); 
   void sync() {
      byte [] superBlock = new byte[Disk.blockSize];
      SysLib.int2bytes(totalBlocks, superBlock, 0);
      SysLib.int2bytes(totalInodes, superBlock, 4);
      SysLib.int2bytes(freeList, superBlock, 8);
      SysLib.rawwrite(0, superBlock); 
   }

   void format( int files ) {
      // initialize the superblock
      // initialize each inode and immediately write it back to disk
      // initialize free blocks

   }
   
   // get a new free block from the freelist
   public int getFreeBlock() {
      if (freeList > 0 && freeList < totalBlocks) {
         byte [] superBlock = new byte[Disk.blockSize];
         SysLib.rawread(freeList, superBlock);
         int freeBlockNumber = freeList;
         freeList = SysLib.bytes2int(superBlock, 0);
         return freeBlockNumber;
      }
      return -1;
   }

   public boolean returnBlock( int oldBlockNumber ) {
      // return this old block to the free list. The list can be a stack.
      return true;  // true or false
   }

}

