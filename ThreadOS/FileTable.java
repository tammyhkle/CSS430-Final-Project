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
   public final static short APPEND = 0;
   public final static short READONLY = 1;
   public final static short WRITEONLY = 2;
   public final static short READWRITE = 3;

   // Class constants for inode states
   public final static short UNUSED = 0; 
   public final static short USED = 1;
   public final static short READ = 2; 
   public final static short WRITE = 3; 
   public final static short DELETE = 4; 
   
   // Instance variables
   private Vector < FileTableEntry > table; // the actual entity of this file table
   private Directory dir; // the root directory

   // Constructor
   public FileTable(Directory directory) { // constructor
      table = new Vector < FileTableEntry > (); // instantiate a file (structure) table
      dir = directory; // receive a reference to the Director from the file system
   }

   /* FALLOC */
   // allocate a new file (structure) table entry for this file name
   // allocate/retrieve and register the corresponding inode using dir
   // increment this inode's count
   // immediately write back this inode to the disk
   // return a reference to this file (structure) table entry
   public synchronized FileTableEntry falloc(String filename, String mode) {

      short inputMode = getMode(mode);
      short iNumber = dir.namei(filename);
      Inode inode = null;

      while (true) {
         if ((iNumber) == (short) -1) {
            iNumber = dir.ialloc(filename);

            if (inputMode == READONLY) {
               return null;
            }

            if ((iNumber) == (short) -1) {
               return null;
            }
            inode = new Inode(iNumber);
            break;

         } else {
            
            inode = new Inode(iNumber);
            if (inode.flag == USED || inode.flag == UNUSED) {
               break;
            } else if (inode.flag == DELETE) {
               return null;
            } else if (inode.flag == READ && inputMode == READONLY) {
               break;
            } else {
               try {
                  wait();
               } catch (InterruptedException e) {}
            }

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

   // helper method for falloc()
   public short getMode(String string) {
      if (string.equals("a")) {
         return APPEND;
      } else if (string.equals("w")) {
         return WRITEONLY;
      } else if (string.equals("r")) {
         return READONLY;
      } else if (string.equals("w+")) {
         return READWRITE;
      } else {
         return -1;
      }
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