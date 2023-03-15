/*
 * @file FileSystem.java 
 * @author Tammy Le & Dani Shaykho
 * @brief CSS 430B O.S.
 * File system we implement the minimum 8 system calls: [d] format, open, close, delete, [t] read, write, seek, fize
 * Other system call: [t] deallocBlocks
 * @date 03/06/2023
 */

public class FileSystem {
   // Instance variables
   private SuperBlock superblock;
   private Directory directory;
   private FileTable filetable;

   // Class constants for inode states
   public final static short UNUSED = 0; 
   public final static short USED = 1;
   public final static short READ = 2; 
   public final static short WRITE = 3; 
   public final static short DELETE = 4; 

   private final int SEEK_SET = 0; // set file pointer to offset
   private final int SEEK_CUR = 1; // set file pointer to current plus offset
   private final int SEEK_END = 2; // set file pointer to EOF plus offset

   // Constructor
   public FileSystem(int diskBlocks) {
      superblock = new SuperBlock(diskBlocks);
      // System.out.println("" + superblock.totalInodes);
      directory = new Directory(superblock.totalInodes);
      filetable = new FileTable(directory);

      // read the "/" file from disk
      FileTableEntry dirEntry = open("/", "r");
      int dirSize = fsize(dirEntry);

      if (dirSize > 0) {
         // directory has data
         byte[] dirData = new byte[dirSize];
         read(dirEntry, dirData);
         directory.bytes2directory(dirData);
      }
      close(dirEntry);

   }

   /* FORMAT */
   // formats the disk, (i.e., Disk.java's data contents). The parameter “files”
   // specifies the maximum number of files to be created, (i.e., the number of
   // inodes to be allocated) in your file system. The return value is 0 on
   // success, otherwise -1.
   public boolean format(int files) {
      superblock.format(files);
      directory = new Directory(superblock.totalInodes);
      filetable = new FileTable(directory);
      return true;
   }

   /* OPEN */
   // opens the file specified by the fileName string in the given mode (where "r"
   // = ready only, "w" = write only, "w+" = read/write, "a" = append), and
   // allocates a new file descriptor, fd to this file. The file is created if it
   // does not exist in the mode "w", "w+" or "a". SysLib.open must return a
   // negative number as an error value if the file does not exist in the mode "r".
   // Note that the file descriptors 0, 1, and 2 are reserved as the standard
   // input, output, and error, and therefore a newly opened file must receive a
   // new descriptor numbered in the range between 3 and 31. If the calling
   // thread's user file descriptor table is full, SysLib.open should return an
   // error value. The seek pointer is initialized to zero in the mode "r", "w",
   // and "w+", whereas initialized at the end of the file in the mode "a".
   public FileTableEntry open(String fileName, String mode) {
      // filetable entry is allocated
      FileTableEntry ftEntry = filetable.falloc(fileName, mode);
      if (mode.equals("w")) { // all blocks belonging to this file is
         if (deallocAllBlocks(ftEntry) == false) { // released
            return null;
         }
      }
      return ftEntry;
   }

   /* CLOSE */
   // closes the file corresponding to fd, commits all file transactions on this
   // file, and unregisters fd from the user file descriptor table of the calling
   // thread's TCB. The return value is 0 in success, otherwise -1.
   public boolean close(FileTableEntry ftEntry) {
      synchronized (ftEntry) {

         ftEntry.count--; // decrement user count

         // if no more users, remove file table entry
         if (ftEntry.count > 0) {
            return true;
         }

      }
      return filetable.ffree(ftEntry);
   }

   /* READ */
   // reads up to buffer.length bytes from the file indicated by fd, starting at
   // the position currently pointed to by the seek pointer. If bytes remaining
   // between the current seek pointer and the end of file are less than
   // buffer.length, SysLib.read reads as many bytes as possible, putting them into
   // the beginning of buffer. It increments the seek pointer by the number of
   // bytes to have been read. The return value is the number of bytes that have
   // been read, or a negative value upon an error (-1)
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
			
			readBuffer = new byte[Disk.blockSize];
			seekPtr = ftEntry.seekPtr;                         // retreive seekPtr position
			currentPosition = 0;

			while(currentPosition < bufferSize) {
				
            // block = ftEntry.inode.findTargetBlock(ftEntry.seekPtr);
				offset = seekPtr % Disk.blockSize;              // set offset
            diskBytes = Disk.blockSize - offset;            // bytes remaining in disk
            remainingBytes = bufferSize - currentPosition;    // bytes remaining to read

				// choose smallest: buffer or disk (avoid null ptr exception)
				if (diskBytes > remainingBytes) {
					toRead = remainingBytes;
            } else {
               toRead = diskBytes;
            }

				block = inode.findTargetBlock(offset);
				
            // verify block
            if (block == -1) {
					return -1;
            }
            else if (block < 0 || offset >= superblock.totalBlocks) {
					break;
            }

				if (offset == 0) {
					readBuffer = new byte[Disk.blockSize];
            }

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
      System.out.println("Content: " + currentPosition );
		return currentPosition;
   }

   /* WRITE */
   // writes the contents of buffer to the file indicated by fd, starting at the
   // position indicated by the seek pointer. The operation may overwrite existing
   // data in the file and/or append to the end of the file. SysLib.write
   // increments the seek pointer by the number of bytes to have been written. The
   // return value is the number of bytes that have been written, or a negative
   // value upon an error (-1)
   public int write(FileTableEntry ftEntry, byte[] buffer) {
      // initialize variables
      short block;
      int offset, bytesRemaining, diskBytes, toWrite, currentPosition, seekPtr;

      // Checking for invalid states
      if (ftEntry == null || ftEntry.mode.equals("r")) { // file is null or in read mode
         return -1;
      } 

      Inode ftEntryInode = ftEntry.inode; //make sure to have ftEntry.inode after checking if it's NOT null.
      if (ftEntryInode == null) { // There is no inode.
         return -1;
      } else if (ftEntryInode.flag == READ || ftEntryInode.flag == WRITE || ftEntryInode.flag == DELETE) { // inode in invalid flag state
         return -1;
      }

      int bufferSize = buffer.length;

      // synchronize to prevent race conditions
      synchronized (ftEntry) {
         // if the mode is append, set the seek pointer to the end of the block
         if (ftEntry.mode.equals("a")) {
            seekPtr = seek(ftEntry, 0, 2); // set seek pointer to end of file
         } else {
            seekPtr = ftEntry.seekPtr; // otherwise, set the seek pointer to the last position
         }

         // set flag to write
         ftEntryInode.flag = WRITE;
         currentPosition = 0;
         byte[] writeData = new byte[Disk.blockSize];

         // run loop as long as buffer isn't empty
         while (currentPosition < bufferSize) {
            // setting offset, remaining bytes in buffer, and remaining bytes on disk variables
            offset = seekPtr % Disk.blockSize;
            bytesRemaining = bufferSize - currentPosition;
            diskBytes = Disk.blockSize - offset;

            // if the space requested is greater than the space remaining on the disk,
            // write until the space is filled. otherwise write the whole thing
            if (diskBytes < bytesRemaining) {
               toWrite = diskBytes;
            } else {
               toWrite = bytesRemaining;
            }

            // gets targetBlock
            block = (short) ftEntryInode.findTargetBlock(offset);
            if (block == -1) { // block does not exist
               // Try to allocate new block, check if out of memory
               block = (short) superblock.getFreeBlock();
               if (block == -1) { // no blocks available
                  // error out of memory, set delete flag then break
                  ftEntryInode.flag = DELETE;
                  break;
               }

               // if the block couldn't be found
               if (ftEntryInode.registerTargetBlock(seekPtr, block) == -1) { // unable to set block
                  // attempt to set index block, set delete flag and break on any errors
                  if (!ftEntryInode.registerIndexBlock(block)) { // unable to set index block
                     ftEntryInode.flag = DELETE;
                     break;
                  }
                  // get a new free block
                  block = (short) superblock.getFreeBlock();
                  if (block == -1) { // no blocks available
                     ftEntryInode.flag = DELETE;
                     break;
                  }

                  // setup the new block
                  if (ftEntryInode.registerTargetBlock(seekPtr, block) == -1) { // unable to set target block
                     ftEntryInode.flag = DELETE;
                     break;
                  }
               }
            }

            // if the block is not in the total block range, set flag to delete and exit
            if (block >= superblock.totalBlocks) {
               ftEntryInode.flag = DELETE;
               break;
            }
            // initialize writeData buffer 
            if (offset == 0) {
               writeData = new byte[Disk.blockSize];
            }
            // read the data to the data byte array
            SysLib.rawread(block, writeData);
            System.arraycopy(buffer, currentPosition, writeData, offset, toWrite); // copy the new data to the array
            SysLib.rawwrite(block, writeData); // write it back
            currentPosition += toWrite; // increment variables
            seekPtr += toWrite;
         }
         // Leaving the while loop.
         // If the file size increased, update the inode's length.
         if (seekPtr > ftEntryInode.length) {
            ftEntryInode.length = seekPtr;
         }
         // Re-set the new seek position.
         seek(ftEntry, currentPosition, 1);

         // This will set flag to used
         if (ftEntryInode.flag != DELETE) {
            ftEntryInode.flag = USED;
         }
         // Then saves to disk at iNumber.
         ftEntryInode.toDisk(ftEntry.iNumber);
      }
      // Return current position.
      return currentPosition;
   }

   /* SEEK */
   // Updates the seek pointer corresponding to fd as follows:
   public synchronized int seek(FileTableEntry ftEntry, int offset, int whence) {
      if(ftEntry == null) {
         return -1;
      } 

      // synchronize on the entry object to ensure exclusive access
      synchronized (ftEntry) {
         // use if-else statements to check the value of location
         if (whence == SEEK_SET) {
            // if location is SEEK_SET, set seek pointer to the offset of beginning of file
            ftEntry.seekPtr = offset;
         }
         if (whence == SEEK_CUR) {
            // if location is SEEK_CUR, add the offset to the current seek pointer
            ftEntry.seekPtr = ftEntry.seekPtr + offset;
         }
         if (whence == SEEK_END) {
            // if location is SEEK_END, set seek pointer to the size of file plus the offset
            ftEntry.seekPtr = fsize(ftEntry) + offset;
         } 

         // ensure that the seek pointer does not become negative
         if (ftEntry.seekPtr < 0) {
            ftEntry.seekPtr = 0;
         } else if (ftEntry.seekPtr > fsize(ftEntry)) { // ensure that the seek pointer does not exceed the size of the file
            ftEntry.seekPtr = fsize(ftEntry);
         }
      } 
      // return the new seek pointer value
      return ftEntry.seekPtr;
   }

   /* DELETE */
   // destroys the file specified by fileName. If the file is currently open, it is
   // not destroyed until the last open on it is closed, but new attempts to open
   // it will fail.
   public boolean delete(String fileName) {
      FileTableEntry ftEntry = open(fileName, "w");
      short iNumber = ftEntry.iNumber;
      return close(ftEntry) && directory.ifree(iNumber);
   }

   /* FSIZE */
   // returns the size in bytes of the file indicated by fd.
   // Unless specified otherwise, each of the above system calls returns -1
   // negative when detecting an error.
   public int fsize(FileTableEntry ftEntry) {
      synchronized(ftEntry) {
         if (ftEntry.inode != null) {
            return ftEntry.inode.length;
         }
         return -1; // error
      }
   }

   /* DEALLOCALLBLOCKS */
   // derived from lecture. This is called when a file is being closed or deleted
   public boolean deallocAllBlocks(FileTableEntry ftEntry) {
      // busy wait until three are no threads accessing this inode
      // ensure this is the only one thread accessing the inode, so we can deallocate
      // block
      if (ftEntry.inode.count != 1) { // there is only one writer
         return false;
      }
      // unregister the index block from inode and return as byte array
      byte[] indexBlock = ftEntry.inode.unregisterIndexBlock();
      if (indexBlock != null) {
         int offset = 0;
         short blockNumber;
         // loop thru the block numbers and return each block to the superblock
         while ((blockNumber = SysLib.bytes2short(indexBlock, offset)) != -1) {
            superblock.returnBlock((int) blockNumber);
         }
      }
      // loop thru all direct blocks and return each one to superblock
      for (int i = 0; i < ftEntry.inode.directSize; i++) {
         if (ftEntry.inode.direct[i] != -1) {
            superblock.returnBlock(ftEntry.inode.direct[i]);
            // mark the corresponding entry in inode's direct block array as unused, refer
            // to inode.java
            ftEntry.inode.direct[i] = -1;
         }
      }
      // save the inode changes to the disk
      ftEntry.inode.toDisk(ftEntry.iNumber);
      // then return true aka success
      return true;
   }

   public void sync() {
      byte[] tempData = directory.directory2bytes();
      FileTableEntry root = open("/", "w");
      write(root, tempData);
      close(root);
      superblock.sync();
   }
}