import ThreadOS.Inode;

public class SuperBlock {

   private final int defaultInodeBlocks = 64;
   public int totalBlocks; // the number of disk blocks
   public int totalInodes; // the number of inodes
   public int freeList; // the block number of the free list's head

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
      // error handling
      if (files < 0) {
         files = defaultInodeBlocks;
      }

      // Initialize each inode and write it back to disk
      for (int i = 0; i < files; i++) {
         Inode inode = new Inode();
         inode.toDisk((short) i); // Look at Inode.java
      }

      // set up freelist
      if (files % 16 == 0) {
         freeList = files / 16 + 1;
      } else {
         freeList = files / 16 + 2;
      }
      // Write the initialized free blocks to the disk.
      for (int i = freeList; i < defaultInodeBlocks - 1; i++) {
         byte[] emptyBlock = new byte[Disk.blockSize]; // Create a new block of zeros.

         // Setting emptyBlock to empty (0)
         for (int j = 0; j < Disk.blockSize; j++ ) {
            emptyBlock[j] = 0;
         }
         SysLib.int2bytes(i + 1, emptyBlock, 0); // Write the block number to the first four bytes of the block.
         SysLib.rawwrite(i, emptyBlock); // Write the block to the disk.
      }

      // Now, add the last blocks
      byte[] lastBlock = new byte[Disk.blockSize];
      SysLib.int2bytes(-1, lastBlock, 0); 
      SysLib.rawwrite(defaultInodeBlocks - 1, lastBlock); // Write the last block to the disk.

      // Update the SuperBlock on the disk.
      sync();
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
      
      if(oldBlockNumber > 0 && oldBlockNumber < totalBlocks) {   // check valid block #
         int tempBlock;
         int nextBlock = freeList;

			byte[] next = new byte[Disk.blockSize];
			byte[] newBlock = new byte[Disk.blockSize];

			// clear newBlock
			for(int i = 0; i < Disk.blockSize; i++) {
				newBlock[i] = 0;
			}

			SysLib.int2bytes(-1, newBlock, 0);

			while (nextBlock != -1) {           // while not at end of list
				SysLib.rawread(nextBlock, next);

				tempBlock = SysLib.bytes2int(next, 0);

				if (tempBlock == -1) {
					// set next free
					SysLib.int2bytes(oldBlockNumber, next, 0);
					SysLib.rawwrite(nextBlock, next);
					SysLib.rawwrite(oldBlockNumber, newBlock);

					return true;    //operation complete
				}

				nextBlock = tempBlock;
			}
      }
      
      return false;
   }

}
