/*
 * @file FileSystem.java 
 * @author Tammy Le & Dani Shaykho
 * @brief CSS 430B O.S.
 * File system we implement 8 system calls: format, open, read, write, seek, close, delete, fize
 * All comments derived from program specifications
 * @date 03/06/2023
 */

public class FileSystem {

   // define the maximum number of files that can be open at once
   private static final int MAX_OPEN_FILES = 32;

   // define the open file table
   private FileTableEntry[] openFileTable;

   private SuperBlock superblock;
   private Directory directory;
   private FileTable filetable;

   public FileSystem(int diskBlocks) {

      // initialize the open file table with MAX_OPEN_FILES entries
      openFileTable = new FileTableEntry[MAX_OPEN_FILES];

      superblock = new SuperBlock(diskBlocks);
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

      // ADD other methods for managing the file system...

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
      if (mode.equals("w")) { // all blocks belonging to thhis file is
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
         if (ftEntry.count == 0) {
            return filetable.ffree(ftEntry);
         }

         return false;
      }
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
      // Check that entry and buffer are not null
      if (ftEntry == null || buffer == null) {
         return -1;
      }

      // Check that the entry's mode is not write or append
      if (ftEntry.mode.equals("w") || ftEntry.mode.equals("a")) {
         return -1;
      }

      // Get the size of the buffer and initialize variables for tracking read
      // progress
      int bufferSize = buffer.length;
      int bytesRead = 0;
      int remainingBytes = bufferSize;
      int blockIndex = 0;

      synchronized (ftEntry) {
         // Loop until all requested data has been read or an error occurs
         while (bytesRead < bufferSize && ftEntry.seekPtr < fsize(ftEntry)) {
            // Calculate the index of the block to read from and read the block data
            int blockNumber = ftEntry.inode.findTargetBlock(ftEntry.seekPtr);
            if (blockNumber == -1) {
               return -1;
            }
            byte[] blockData = new byte[Disk.blockSize];
            SysLib.rawread(blockNumber, blockData);

            // Calculate the offset within the block and the number of bytes left to read
            // from the block
            int blockOffset = ftEntry.seekPtr % Disk.blockSize;
            int bytesLeftInBlock = Disk.blockSize - blockOffset;

            // Calculate the number of bytes to read from this block and adjust
            // remainingBytes accordingly

            int bytesToRead = Math.min(remainingBytes, bytesLeftInBlock); // math.min returns the smaller value between
                                                                          // 2 values

            // int bytesToRead;
            // if (remainingBytes > bytesLeftInBlock) {
            // bytesToRead = bytesLeftInBlock;
            // } else {
            // bytesToRead = remainingBytes;
            // }
            // remainingBytes -= bytesToRead;

            // Copy the data from the block to the buffer and update seekPtr and bytesRead
            System.arraycopy(blockData, blockOffset, buffer, blockIndex, bytesToRead);
            ftEntry.seekPtr += bytesToRead;
            bytesRead += bytesToRead;
            blockIndex += bytesToRead;
         }

         // Return the number of bytes read
         return bytesRead;
      }
   }

   /* WRITE */
   // writes the contents of buffer to the file indicated by fd, starting at the
   // position indicated by the seek pointer. The operation may overwrite existing
   // data in the file and/or append to the end of the file. SysLib.write
   // increments the seek pointer by the number of bytes to have been written. The
   // return value is the number of bytes that have been written, or a negative
   // value upon an error (-1)
   public int write(FileTableEntry ftEntry, byte[] buffer) {
      // make sure that entry is valid and not read-only mode, if so return error
      if (ftEntry == null || ftEntry.mode.equals("r")) {
         return -1;
      }

      // initialize variables for tracking write progress
      int bytesWritten = 0;
      int bufferSize = buffer.length;

      // synchronize ftEntry to make sure we can access file data
      synchronized (ftEntry) {
         // more initializing to track block data and offsets during write
         int blockNumber;

         // while there is sill data to write and seekPtr is within file size
         while (bufferSize > 0 && ftEntry.seekPtr < fsize(ftEntry)) {
            // find block # and offset for current seekPtr position
            blockNumber = ftEntry.inode.findTargetBlock(ftEntry.seekPtr);

            if (blockNumber == -1) {
               // if block doesn't exist yet, then need to allocate a new block
               int newBlockNumber = superblock.getFreeBlock();

               int blockTestPtr = ftEntry.inode.findIndexBlock();

               if (blockTestPtr == -3) {
                  int getFreeBlock = superblock.getFreeBlock();
                  if (ftEntry.inode.registerIndexBlock((short) getFreeBlock)) {
                     return -1;
                  }
                  if (ftEntry.inode.findIndexBlock() != 0) {
                     return -1;
                  }
               } else if (blockTestPtr == -1 || blockTestPtr == -2) {
                  return -1;
               }
               blockNumber = newBlockNumber;
            }
            byte[] tempBuffer = new byte[Disk.blockSize];
            SysLib.rawread(blockNumber, tempBuffer);

            int tempPtr = ftEntry.seekPtr % Disk.blockSize;
            int difference = Disk.blockSize - tempPtr;

            // calculate block offset and number of bytes left in the block
            if (difference > bufferSize) {
               // read block data from disk, and write the new data into buffer, and write
               // buffer back to disk
               System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, bufferSize);
               SysLib.rawwrite(blockNumber, tempBuffer);
               // update seekPtr - how many bytes written, and the buffer size for next
               // iteration
               ftEntry.seekPtr += bufferSize;
               bytesWritten += bufferSize;
               bufferSize = 0;
            } else {
               // read block data from disk, and write the new data into buffer, and write
               // buffer back to disk
               System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, difference);
               SysLib.rawwrite(blockNumber, tempBuffer);
               // update seekPtr - how many bytes written, and the buffer size for next
               // iteration
               ftEntry.seekPtr += difference;
               bytesWritten += difference;
               bufferSize -= difference;
            }
         }
         // set ftEntry objst seekPtr
         if (ftEntry.seekPtr > ftEntry.inode.length) {
            ftEntry.inode.length = ftEntry.seekPtr;
         }
         ftEntry.inode.toDisk(ftEntry.iNumber);
      }
      return bytesWritten;
   }

   private final int SEEK_SET = 0; // set file pointer to offset
   private final int SEEK_CUR = 1; // set file pointer to current plus offset
   private final int SEEK_END = 2; // set file pointer to EOF plus offset

   /* SEEK */
   // Updates the seek pointer corresponding to fd as follows:
   public synchronized int seek(FileTableEntry ftEntry, int offset, int whence) {

      // synchronize on the entry object to ensure exclusive access
      synchronized (ftEntry) {
         // use if-else statements to check the value of location
         if (whence == SEEK_SET) {
            // if location is SEEK_SET, set seek pointer to the offset of beginning of file
            ftEntry.seekPtr = offset;
         } else if (whence == SEEK_CUR) {
            // if location is SEEK_CUR, add the offset to the current seek pointer
            ftEntry.seekPtr = ftEntry.seekPtr + offset;
         } else if (whence == SEEK_END) {
            // if location is SEEK_END, set seek pointer to the size of file plus the offset
            ftEntry.seekPtr = ftEntry.inode.length + offset;
         } else {
            // if location is not one of the valid options, return -1 (unsuccessful)
            return -1;
         }

         // ensure that the seek pointer does not become negative
         if (ftEntry.seekPtr < 0) {
            ftEntry.seekPtr = 0;
         }

         // ensure that the seek pointer does not exceed the size of the file
         if (ftEntry.seekPtr > ftEntry.inode.length) {
            ftEntry.seekPtr = ftEntry.inode.length;
         }

<<<<<<< HEAD
         return true;
		}
=======
         // return the new seek pointer value
         return ftEntry.seekPtr;
      }
>>>>>>> 389821e1156e50072be988b0de0a1f22761e4473
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
      if (ftEntry.inode != null) {
         return ftEntry.inode.length;
      }
      return -1; // error
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
      for (int i = 0; i < Inode.directSize; i++) {
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
}