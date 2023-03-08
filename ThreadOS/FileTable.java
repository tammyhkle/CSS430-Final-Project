import java.util.Vector;

public class FileTable {
   private Vector<FileTableEntry> table; // the actual entity of this file table
   private Directory dir; // the root directory

   public FileTable(Directory directory) { // constructor
      table = new Vector<FileTableEntry>(); // instantiate a file (structure) table
      dir = directory; // receive a reference to the Director from the file system
   }

   /* FALLOC */
   // mostly derived from the professor pdf
   // major public methods
   // look at FileTableEntry.java and Directory.java
   public synchronized FileTableEntry falloc(String filename, String mode) {
      // allocate a new file (structure) table entry for this file name
      // allocate/retrieve and register the corresponding inode using dir
      // increment this inode's count
      // immediately write back this inode to the disk
      // return a reference to this file (structure) table entry

      short Number = -1;
      Inode inode = null;

      while (true) {
         // Look up the inode for the given filename in the directory.
         short inumber;
         if (filename.equals("/")) {
            inumber = 0;
         } else {
            inumber = dir.namei(filename);
         }

         if (inumber >= 0) {
            // If the inode already exists, get it from disk.
            inode = new Inode(inumber);

            // Check if the file is being opened for reading.
            if (mode.equals("r")) { // if ( mode.compareTo( "r")) { } "compareTo" causes error
               // If the file is not currently being written, mark it as being read and return.
               if (inode.flag != WRITE) { // no need to wait
                  inode.flag = READ;
                  break;
               }

               // Otherwise, wait for the write lock to be released.
               try {
                  wait(); // wait for a write to exit
               } catch (InterruptedException ex) {
                  // Restore the interrupted status and try again.
                  Thread.currentThread().interrupt();
               }

            } else {
               // If the file is being opened for writing, mark it as being written and return.
               if (inode.flag == UNUSED || inode.flag == USED) {
                  inode.flag = WRITE;
                  break;
               }

               // If the file is already being written, wait for the write lock to be released.
               if (inode.flag == WRITE) {
                  try {
                     wait();
                  } catch (InterruptedException ex) {
                     // Restore the interrupted status and try again.
                     Thread.currentThread().interrupt();
                  }
               }
            }

         } else {
            if (!mode.equals("r")) {
               // If the inode does not exist and the file is not being opened for reading,
               // allocate a new inode for the file and mark it as being written.
               inumber = dir.ialloc(filename);
               inode = new Inode(inumber);
               inode.flag = WRITE;
               break;

            } else {
               // If the inode does not exist and the file is being opened for reading,
               // return null to indicate that the file could not be opened.
               return null;
            }
         }

         // Update the inode on disk and create a new file table entry for the file.
         // this is derived from the professor
         inode.count++;
         inode.toDisk(inode.iNumber);
         FileTableEntry entry = new FileTableEntry(inode, inode.iNumber, mode);
         table.addElement(entry); // create a table entry and register it
         return entry;
      }
   }

   /* FFREE */
   public synchronized boolean ffree(FileTableEntry e) {
      // receive a file table entry reference
      // save the corresponding inode to the disk
      // free this file table entry.
      // return true if this file table entry found in my table
   }

   /* FEMPTY */
   public synchronized boolean fempty() {
      return table.isEmpty(); // return if table is empty
   } // should be called before starting a format

}
