import ThreadOS.Inode;

public class SuperBlock {

   private final int defaultInodeBlocks = 64;
   public int totalBlocks; // the number of disk blocks
   public int totalInodes; // the number of inodes
   public int freeList; // the block number of the free list's head
   private int totalFreeBlocks;

   // derived from professor's pdf
   public SuperBlock(int diskSize) {
      // read the superblock from disk.
      byte[] superBlock = new byte[Disk.blockSize];
      SysLib.rawread(0, superBlock);
      totalBlocks = SysLib.bytes2int(superBlock, 0);
      totalInodes = SysLib.bytes2int(superBlock, 4);
      freeList = SysLib.bytes2int(superBlock, 8);

      // check disk contents are valid.
      if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2) {
         return;
      } else { // if invalid, call format( ).
         totalBlocks = diskSize;
         format(defaultInodeBlocks);
      }
   }

   // write back in-memory superblock to disk: SysLib.rawwrite( 0, superblock )
   // aka write back totalBlocks, inodeBlocks, and freeList to disk
   void sync() {
      byte[] superBlock = new byte[Disk.blockSize];
      SysLib.int2bytes(totalBlocks, superBlock, 0);
      SysLib.int2bytes(totalInodes, superBlock, 4);
      SysLib.int2bytes(freeList, superBlock, 8);
      SysLib.rawwrite(0, superBlock);
   }

   public void format(int files) {
      // Initialize the SuperBlock
      SuperBlock superBlock = new SuperBlock(files);

      // Initialize each inode and write it back to disk
      for (int i = 0; i < files; i++) {
         Inode inode = new Inode();
         inode.toDisk((short) i); // Look at Inode.java
      }

      // Initialize free blocks
      int freeBlockCount = superBlock.totalBlocks;

      // Write block numbers to each block, except the last one
      for (int i = 0; i < freeBlockCount - 1; i++) {
         byte[] blockData = new byte[Disk.blockSize];
         SysLib.int2bytes(i + 1, blockData, 0); // Write the block number to the first four bytes of the block.
         SysLib.rawwrite(i + 1, blockData); // Write the block to the disk.
      }

      // Set up the free list in the superblock
      superBlock.freeList = 1; // Start with the first block to allocate new file
      superBlock.totalFreeBlocks = freeBlockCount - 1; // All blocks except the last one are free

      // Write block numbers to the remaining free block
      byte[] lastBlock = new byte[Disk.blockSize];
      SysLib.int2bytes(-1, lastBlock, 0); // Set the first four bytes to -1 to indicate the end of the free list.
      SysLib.rawwrite(freeBlockCount - 1, lastBlock); // Write the last block to the disk.

      // Write the initialized free blocks to the disk.
      for (int i = superBlock.freeList; i < superBlock.totalBlocks; i++) {
         byte[] block = new byte[Disk.blockSize]; // Create a new block of zeros.
         SysLib.int2bytes(i + 1, block, 0); // Write the block number to the first four bytes of the block.
         SysLib.rawwrite(i, block); // Write the block to the disk.
      }

      // Update the SuperBlock on the disk.
      superBlock.sync();
   }

   // dequeue the top block from the free list
   public int getFreeBlock() {
      if (freeList > 0 && freeList < totalBlocks) {
         byte[] superBlock = new byte[Disk.blockSize];
         SysLib.rawread(freeList, superBlock);
         int freeBlockNumber = freeList;
         freeList = SysLib.bytes2int(superBlock, 0);
         return freeBlockNumber;
      }
      return -1;
   }

   // enqueue a given block to the end of the free list
   public boolean returnBlock(int oldBlockNumber) {
      // return this old block to the free list. The list can be a stack.
      
      if(oldBlockNumber > 0 && oldBlockNumber < totalBlocks) {
         
      }
      
      return true;
   }

}
