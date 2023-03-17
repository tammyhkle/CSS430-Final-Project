   public synchronized int read(FileTableEntry ftEnt, byte[] buffer) {
      if (ftEnt.mode == "w" || ftEnt.mode == "a") {
         return -1;
      }
   
      int offset = 0; // buffer offset
      int left = buffer.length; // the remaining data of this buffer
   
      synchronized(ftEnt) {
         while (left > 0 && ftEnt.seekPtr < fsize(ftEnt)) {
            // repeat reading until no more data or reaching EOF
   
            // get the block pointed to by the seekPtr
            int targetBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
            if (targetBlock == -1) {
               break;
            }
   
            //System.out.printin( "read( ) targetBlock=" + targetBlock);
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(targetBlock, data);
   
            // find the offset in the current block to read
            int offsetInBlock = ftEnt.seekPtr % Disk.blockSize;
   
            // compute the bytes to read
            int blkLeft = Disk.blockSize - offsetInBlock;
            int fileLeft = fsize(ftEnt) - ftEnt.seekPtr;
            int smallest = Math.min(Math.min(blkLeft, left), fileLeft);
   
            System.arraycopy(data, offsetInBlock, buffer, offset, smallest);
            
            // update the seek pointer, offset and left in buffer 
            ftEnt.seekPtr += smallest;
            offset += smallest;
            left -= smallest;
         }
         return offset;
      }
   }
   
   public synchronized int read(FileTableEntry ftEntry, byte[] buffer) {

		Inode inode;
      byte[] readBuffer;
      int bufferSize = buffer.length;
		int block, seekPtr, offset, diskBytes, remainingBytes, toRead, currentPosition;

		if(ftEntry == null) {
			return -1;
      }

		// Check that the entry's mode is not append or write 
		if ((ftEntry.mode.equals("a")) || (ftEntry.mode.equals("w"))) {
			return -1;
      }
      
      inode = ftEntry.inode;
		if (inode == null) {
			return -1;
      }

		synchronized(ftEntry) {
			
			// readBuffer = new byte[Disk.blockSize];
			seekPtr = ftEntry.seekPtr;                         // retreive seekPtr position
			currentPosition = 0;

			while(currentPosition < bufferSize) {
				
            readBuffer = new byte[Disk.blockSize];

            // block = ftEntry.inode.findTargetBlock(ftEntry.seekPtr);
				offset = seekPtr % Disk.blockSize;                 // set offset
            diskBytes = Disk.blockSize - offset;               // bytes remaining in disk
            remainingBytes = bufferSize - currentPosition;     // bytes remaining to read

				// choose smallest: buffer or disk (avoid null ptr exception)
				if (diskBytes > remainingBytes) {
					toRead = remainingBytes;
            } else {
               toRead = diskBytes;
            }

				block = inode.findTargetBlock(ftEntry.seekPtr);
				
            // verify block
            if (block == -1) {
					return -1;
            }
            else if (block < 0 || offset >= superblock.totalBlocks) {
					break;
            }

				// if (offset == 0) {
				// 	readBuffer = new byte[Disk.blockSize];
            // }

				// read disk contents into buffer
				if (SysLib.rawread(block, readBuffer) == -1) {
					return -1;
            }

				//copy read data into buffer
				System.arraycopy(readBuffer, offset, buffer, currentPosition, toRead);
				currentPosition += toRead;
				seekPtr += toRead;
			}

			// update seekPtr 
			seek(ftEntry, currentPosition, 1);
		}

		// return last read position
		return currentPosition;
   }