/*
 * @file FileTable.java 
 * @author Tammy Le
 * @brief CSS 430B O.S.
 * File table - mostly derived from the professor pdf. Major public methods: fallor and ffree
   (look at FileTableEntry.java and Directory.java)
 * @date 03/06/2023
 */

import java.util.Vector;

public class FileTable {
   private Vector<FileTableEntry> table; // the actual entity of this file table
   private Directory dir; // the root directory

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
         iNumber = filename.equals("/") ? 0 : dir.namei(filename);
         if (iNumber >= 0) {

            // If the inode already exists, get it from disk.
            inode = new Inode(iNumber);

            // Check if the file is being opened for reading.
            if (mode.equals("r")) { // if ( mode.compareTo( "r")) { } "compareTo" causes error

               // If the file is not currently being written, mark it as being read and return.
               if (inode.flag == 1) { // no need to wait
                  break;
               } else if (inode.flag == 2) {
                  try {
                     wait(); // wait for a write to exit
                  } catch (InterruptedException ex) {
                  }
               } else if (inode.flag == 0) { // to be deleted
                  iNumber = -1; // no more open
                  return null;
               }
            } else if (mode.equals("w")) {
               // If the inode does not exist and the file is not being opened for reading,
               // allocate a new inode for the file and mark it as being written.
               iNumber = dir.ialloc(filename);
               inode = new Inode(iNumber);
               inode.flag = 2;
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
         return ftEntry; // e means FileTableEntry, returning a FileTableEntry object
      }
      return null; // added to to get rid of error "method must return a result of type FileTableEntry"
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
