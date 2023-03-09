/*
 * @file FileSystem.java 
 * @author Tammy Le & Dani Shaykho
 * @brief CSS 430B O.S.
 * File system we implement 8 system calls: format, open, read, write, seek, close, delete, fize
 * All comments derived from program specifications
 * @date 03/06/2023
 */

public class FileSystem {

   public static final int SEEK_SET = 0; // set file pointer to offset
   public static final int SEEK_CUR = 1; // set file pointer to current plus offset
   public static final int SEEK_END = 2; // set file pointer to EOF plus offset

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
   public int format(int files) {
      superblock.format(files);
      directory = new Directory(superblock.totalInodes);
      filetable = new FileTable(directory);
      return 0;
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
      FileTableEntry ftEntry = filetable.falloc(fileName, mode);
      if (mode.equals("w")) {
         if (deallocAllBlocks(ftEntry) == false) {
            return null;
         }
      }
      return ftEntry;
   }

   /* READ */
   // reads up to buffer.length bytes from the file indicated by fd, starting at
   // the position currently pointed to by the seek pointer. If bytes remaining
   // between the current seek pointer and the end of file are less than
   // buffer.length, SysLib.read reads as many bytes as possible, putting them into
   // the beginning of buffer. It increments the seek pointer by the number of
   // bytes to have been read. The return value is the number of bytes that have
   // been read, or a negative value upon an error.
   public int read(int fd, byte buffer[]) {
      if (buffer == null) {
         return -1;
      }
      if (fd < 0 || fd >= FileTable.MAX_OPEN_FILES) {
         return -1;
      }

      FileTableEntry entry = openFileTable[fd];
      if (entry == null) {
         return -1;
      }

      synchronized (entry) {
         int bytesRead = 0;
         int bytesRemain = buffer.length;

         while (bytesRemain > 0) {
            // need to know how many bytes we can read
            int bytesToRead = Math.min(bytesRemain, Disk.blockSize); // for disk, need to import

            // need to compute current block and offset inside that block
            int currentBlock = entry.inode.getBlockNumber(entry.seekPtr);
            int currentOffset = entry.seekPtr % Disk.blockSize;

            // read block from disk into buffer
            byte[] blockBuffer = new byte[Disk.blockSize];
            int bytesReadFromDisk = SysLib.rawread(currentBlock, blockBuffer);

            // any errors?
            if (bytesReadFromDisk < 0) {
               return -1;
            }
            // copy as many bytes as possible from the buffer to the user's buffer
            int bytesCopied = Math.min(bytesReadFromDisk - currentOffset, bytesToRead);
            System.arraycopy(blockBuffer, currentOffset, buffer, bytesRead, bytesCopied);

            // update the seek pointer and counters
            entry.seekPtr += bytesCopied;
            bytesRead += bytesCopied;
            bytesRemain -= bytesCopied;

            // if we reached the end of the file, break out of the loop
            if (entry.seekPtr == entry.inode.length) {
               break;
            }
         }
         return bytesRead;
      }
   }

   /* WRITE */
   // writes the contents of buffer to the file indicated by fd, starting at the
   // position indicated by the seek pointer. The operation may overwrite existing
   // data in the file and/or append to the end of the file. SysLib.write
   // increments the seek pointer by the number of bytes to have been written. The
   // return value is the number of bytes that have been written, or a negative
   // value upon an error.
   public int write(int fd, byte buffer[]) {
      if (buffer == null) {
         return -1;
      }
      if (fd < 0 || fd >= FileTable.MAX_OPEN_FILES) {
         return -1;
      }

      FileTableEntry entry = openFileTable[fd];
      if (entry == null) {
         return -1;
      }

      synchronized (entry) {
         int bytesWritten = 0;
         int bytesRemain = buffer.length;

         while (bytesRemain > 0) {
            // need to know how many bytes we can write
            int bytesToWrite = Math.min(bytesRemain, Disk.blockSize);

            // need to compute current block and offset inside that block
            int currentBlock = entry.inode.getBlockNumber(entry.seekPtr);
            int currentOffset = entry.seekPtr % Disk.blockSize;

            // read the block from disk into a buffer
            byte[] blockBuffer = new byte[Disk.blockSize];
            int bytesReadFromDisk = SysLib.rawread(currentBlock, blockBuffer);

            // check for errors
            if (bytesReadFromDisk < 0) {
               return -1;
            }

            // overwrite or append data to the block buffer
            int bytesWrittenToBlock = Math.min(bytesReadFromDisk - currentOffset, bytesToWrite);
            System.arraycopy(buffer, bytesWritten, blockBuffer, currentOffset, bytesWrittenToBlock);

            // write the modified block buffer to disk
            int bytesWrittenToDisk = SysLib.rawwrite(currentBlock, blockBuffer);

            // check for errors
            if (bytesWrittenToDisk < 0) {
               return -1;
            }

            // update the seek pointer and counters
            entry.seekPtr = entry.seekPtr + bytesWrittenToBlock;
            bytesWritten = bytesWritten + bytesWrittenToBlock;
            bytesRemain = bytesRemain - bytesWrittenToBlock;

            // if the seek pointer exceeds the length of the file, update the file length
            if (entry.seekPtr > entry.inode.length) {
               entry.inode.length = entry.seekPtr;
            }
         }

         // write the updated inode to disk
         entry.inode.toDisk(entry.iNumber);

         return bytesWritten;
      }
   }

   /* SEEK */
   // Updates the seek pointer corresponding to fd as follows:
   public int seek(int fd, int offset, int whence) {
      // assuming that openFileTable is an array of FileTableEntry objects, and we
      // synchronize on it to prevent race conditions when accessing the table
      synchronized (openFileTable) {
         if (fd < 0 || openFileTable[fd] == null || fd >= openFileTable.length) {
            return -1; // The file cannot open, fd is not valid
         }

         FileTableEntry entry = openFileTable[fd];
         int newSeekPtr = 0;

         // 1. If whence is SEEK_SET (= 0), the file's seek pointer is set to offset
         // bytes from the beginning of the file
         if (whence == SEEK_SET) {
            newSeekPtr = offset;
            // 2. If whence is SEEK_CUR (= 1), the file's seek pointer is set to its current
            // value plus the offset. The offset can be positive or negative.
         } else if (whence == SEEK_CUR) {
            newSeekPtr = entry.seekPtr + offset;
            // 3. If whence is SEEK_END (= 2), the file's seek pointer is set to the size of
            // the file plus the offset. The offset can be positive or negative.
         } else if (whence == SEEK_END) {
            newSeekPtr = entry.inode.length + offset;
         } else {
            return -1; // "Error"
         }
         // 4. If the user attempts to set the seek pointer to a negative number you must
         // clamp it to zero. If the user attempts to set the pointer to beyond the file
         // size, you must set the seek pointer to the end of the file. In both cases,
         // you should return success. (So aka we need to clamp seek to a valid range)
         newSeekPtr = Math.max(newSeekPtr, 0);
         newSeekPtr = Math.min(newSeekPtr, entry.inode.length);
         // then update and return new value
         entry.seekPtr = newSeekPtr;
         return entry.seekPtr;
      }
   }

   /* CLOSE */
   // closes the file corresponding to fd, commits all file transactions on this
   // file, and unregisters fd from the user file descriptor table of the calling
   // thread's TCB. The return value is 0 in success, otherwise -1.
   public int close(FileTableEntry ftEntry) {
      synchronized(ftEntry) {
		
			ftEntry.count--;  // decrement user count

         // if no more users, remove file table entry
			if (ftEntry.count == 0) {
				int result = (filetable.ffree(ftEntry)) ? 0 : -1;
            return result;
			}

         return -1;
		}
   }

   /* DELETE */
   // destroys the file specified by fileName. If the file is currently open, it is
   // not destroyed until the last open on it is closed, but new attempts to open
   // it will fail.
   public int delete(String fileName) {
      FileTableEntry ftEntry = open(fileName, "w");
      if (directory.ifree(ftEntry.iNumber) == true && close(ftEntry) == 0) { 
         return 0;
      } else {
         return -1;
      }
   }

   /* FSIZE */
   // returns the size in bytes of the file indicated by fd.
   // Unless specified otherwise, each of the above system calls returns -1
   // negative when detecting an error.
   public int fsize(int fd) {
      return 0;
   }

   /* DEALLOCALLBLOCKS */
   public boolean deallocAllBlocks(FileTableEntry fileTableEntry) {
      return true;
   }
}