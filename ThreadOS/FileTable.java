
/*
 * @file FileTable.java 
 * @author Tammy Le
 * @brief CSS 430B O.S.
 * Implemented: [d] falloc and ffree
 * @date 03/06/2023
 */
import java.util.Vector;

public class FileTable {
   // Class constants for inode flags
   public final static int UNUSED = 0;
   public final static int USED = 1;
   public final static int READ = 2;
   public final static int WRITE = 3;

   // Instance variables
   private Vector<FileTableEntry> table; // the actual entity of this file table
   private Directory dir; // the root directory

   // Constructor
   public FileTable(Directory directory) { // constructor
      table = new Vector<FileTableEntry>(); // instantiate a file (structure) table
      dir = directory; // receive a reference to the Director from the file system
   }

   /* FALLOC */
   // allocate a new file (structure) table entry for this file name
   // allocate/retrieve and register the corresponding inode using dir
   // increment this inode's count
   // immediately write back this inode to the disk
   // return a reference to this file (structure) table entry
   public synchronized FileTableEntry falloc(String filename, String mode) {
      short iNumber = -1;
      Inode inode = null;

      while (true) {

         // Look up the inode for the given filename in the directory.
         iNumber = filename.equals("/") ? (short) 0 : dir.namei(filename);

         // If the file exists
         if (iNumber >= 0) {

            // If the inode already exists, get it from disk.
            inode = new Inode(iNumber);

            // If the mode is Read:
            if (mode.equals("r")) {

               // if its read, used, or unused
               if (inode.flag == READ || inode.flag == USED || inode.flag == UNUSED) {
                  // flag = to read
                  inode.flag = READ;
                  break;
               } else if (inode.flag == WRITE) { // wait if its being written
                  try {
                     wait();
                  } catch (InterruptedException e) {
                  }
               }

            } else { // requested for writing (aka mode is Write)

               // if used or unused
               if (inode.flag == USED || inode.flag == UNUSED) {
                  inode.flag = WRITE;
                  break;
               } else { // wait if its write or read
                  try {
                     wait();
                  } catch (InterruptedException e) {
                  }
               }

            }

            // create new inode for file if node doesn't exist
         } else if (!mode.equals("r")) {
            iNumber = dir.ialloc(filename);
            inode = new Inode(iNumber);
            inode.flag = 3;
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
      inode.toDisk(iNumber);
      FileTableEntry ftEntry = new FileTableEntry(inode, iNumber, mode);
      table.addElement(ftEntry); // create a table entry and register it
      return ftEntry; // returning a FileTableEntry object
   }

   /* FFREE */
   // receive a file table entry reference
   // save the corresponding inode to the disk
   // free this file table entry.
   // return true if this file table entry found in my table
   public synchronized boolean ffree(FileTableEntry ftEntry) { // e means ftEntry
      // Create a new inode object corresponding to the entry's iNumber
      Inode inode = new Inode(ftEntry.iNumber);

      // Attempt to remove the entry from the file table
      if (table.remove(ftEntry)) {
         // If the entry was removed successfully:
         // Decrease the count of users of that file
         inode.count--;

         // If the file was being read from:
         if (inode.flag == 1) {
            // If there are no more readers:
            if (inode.count == 0) {
               // Notify one waiting thread (if any) that the file can be written to
               notify();

               // Set the inode's flag to "used"
               inode.flag = 1;
            }
         }
         // If the file was being written to:
         else if (inode.flag == 2) {
            // Set the inode's flag to "used"
            inode.flag = 2;

            // Notify all waiting threads that the file can be written to
            notifyAll();
         }

         // - Save the corresponding inode to the disk
         inode.toDisk(ftEntry.iNumber);

         // Return true to indicate that the entry was found in the file table and
         // removed
         return true;
      }

      // Return false to indicate that the entry was not found in the file table
      return false;
   }

   /* FEMPTY */
   public synchronized boolean fempty() {
      return table.isEmpty(); // return if table is empty
   } // should be called before starting a format

}
