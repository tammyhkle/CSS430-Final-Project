import java.util.*;

public class Cache {
   private int blockSize;
   private byte[][] cache = null;   // you may use: private byte[][] = null;
   private int victim;
   // private Vector < byte[] > pages;

   private class Entry {
      public static final int INVALID = -1;
      public boolean reference;
      public boolean dirty;
      public int frame;
      public Entry() {
         reference = false;
         dirty = false;
         frame = INVALID;
      }
   }

   private Entry[] pageTable = null;

   private int nextVictim() {
      while (true) {
         victim = ( victim + 1 ) % pageTable.length;  // always start from the next frame
         if(pageTable[victim].reference == false) {
            return victim;
         } else {
            pageTable[victim].reference = false;
         }
      }
   }

   private void writeBack(int victimEntry) {
      if (pageTable[victimEntry].frame != Entry.INVALID &&
         pageTable[victimEntry].dirty == true) {
         SysLib.rawwrite(pageTable[victimEntry].frame, cache[victimEntry]);    // implement by yourself
         pageTable[victimEntry].dirty = false;
      }
   }

   public Cache(int blockSize, int cacheBlocks) {
      cache = new byte[cacheBlocks][blockSize];    // instantiate pages (cache)
      for(int i = 0; i < cacheBlocks; i++) {
         cache[i] = new byte[blockSize];
      }
      
      pageTable = new Entry[cacheBlocks];     // instantiate and initialize pageTable
      for(int i = 0; i < cacheBlocks; i++) {
         pageTable[i] = new Entry();
      }
   }

   public synchronized boolean read(int blockId, byte buffer[]) {
      if (blockId < 0) {
         SysLib.cerr("threadOS: a wrong blockId for cread\n");
         return false;
      }

      // locate a valid page
      for (int i = 0; i < pageTable.length; i++) {
         if (pageTable[i].frame == blockId) {         // cache hit!!
            // copy cache[i] to buffer
            System.arraycopy(cache[i], 0, buffer, 0, cache[i].length);
            pageTable[i].reference = true;
            return true;
         }
      }

      // page miss!!
      int invalidPage = findInvalidPage();
      
      if(invalidPage != -1) {    // if we find an invalid page
         // read the data from the disk to this cache block
         SysLib.rawread(blockId, cache[invalidPage]);
         pageTable[invalidPage].frame = blockId;
         pageTable[invalidPage].reference = true;
         return true;
      }
      
      // if no invalid page is found, all pages are full, seek for a victim
      int victimEntry = nextVictim();

      // write back a dirty copy
      writeBack(victimEntry);

      // read a requested block from disk
      SysLib.rawread(blockId, cache[victimEntry]);

      // cache it
      // copy cache[victimEntry] to buffer
      System.arraycopy(cache[victimEntry], 0, buffer, 0, cache[victimEntry].length);
   
      pageTable[victimEntry].frame = blockId;
      pageTable[victimEntry].reference = true;
      return true;
   }

   public synchronized boolean write(int blockId, byte buffer[]) {
      if (blockId < 0) {
         SysLib.cerr("threadOS: a wrong blockId for cwrite\n");
         return false;
      }

      // locate a valid page
      for (int i = 0; i < pageTable.length; i++) {
         if (pageTable[i].frame == blockId) {         // cache hit
            // copy buffer to cache[i]
            System.arraycopy(buffer, 0, cache[i], 0, buffer.length);
            pageTable[i].reference = true;
            pageTable[i].dirty = true;
            return true;
         }
      }

      // page miss!!
      int invalidPage = findInvalidPage();
      
      if(invalidPage != -1) {    // if we find an invalid page
         // write the data to this cache block. 
         // You do not have to write this data through to the disk device. 
         System.arraycopy(buffer, 0, cache[invalidPage], 0, buffer.length);
         pageTable[invalidPage].frame = blockId;
         pageTable[invalidPage].reference = true;
         pageTable[invalidPage].dirty = true;
         return true;
      }
      
      // if no invalid page is found, all pages are full, seek for a victim
      int victimEntry = nextVictim();

      // write back a dirty copy
      writeBack(victimEntry);

      // cache it but not write through.
      // copy buffer to cache[victimEntry]
      System.arraycopy(buffer, 0, cache[victimEntry], 0, cache[victimEntry].length);

      pageTable[victimEntry].frame = blockId;
      pageTable[victimEntry].reference = true;
      pageTable[victimEntry].dirty = true;
      return true;
   }

   public synchronized void sync() {
      for (int i = 0; i < pageTable.length; i++)
         writeBack(i);
      SysLib.sync();
   }

   public synchronized void flush() {
      for (int i = 0; i < pageTable.length; i++) {
         writeBack(i);
         pageTable[i].reference = false;
         pageTable[i].frame = Entry.INVALID;
      }
      SysLib.sync();
   }

   public int findInvalidPage() {
      for(int i = 0; i < pageTable.length; i++) {
         if(pageTable[i].frame == -1) {
            return i;
         }
      }
      return -1;
   }
}