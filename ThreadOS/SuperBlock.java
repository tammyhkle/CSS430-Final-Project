
public class SuperBlock {
   
   public int totalBlocks;       // the number of disk blocks
   public int totalInodes;       // the number of inodes
   public int freeList;          // the block number of the free list's head

   public SuperBlock(int diskSize) {
      // read the superblock from disk. 
      // check disk contents are valid. 
      // if invalid, call format( ).
   }

   void sync() {
      // write back in-memory superblock to disk: SysLib.rawwrite( 0, superblock ); 
   }

   void format( int files ) {
      // initialize the superblock
      // initialize each inode and immediately write it back to disk
      // initialize free blocks
   }

   public int getFreeBlock() {
      // get a new free block from the freelist
      int freeBlockNumber = 0;
      return freeBlockNumber;
   }

   public boolean returnBlock( int oldBlockNumber ) {
      // return this old block to the free list. The list can be a stack.
      return true;  // true or false
   }

}

