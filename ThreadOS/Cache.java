import java.util.*;

public class Cache {
    private int blockSize;
    private Vector<byte[]> pages; // you may use: private byte[][] = null;
    private int victim;

    private class Entry {
	public static final int INVALID = -1;
	public boolean reference;
	public boolean dirty;
	public int frame;
	public Entry( ) {
	    reference = false;
	    dirty = false;
	    frame = INVALID;
	}
    }
    private Entry[] pageTable = null;

    private int nextVitim( ) {
	// implement by yourself
	return victim;
    }

    private void writeBack( int victimEntry ) {
        if ( pageTable[victimEntry].frame != Entry.INVALID &&
             pageTable[victimEntry].dirty == true ) {
	    call SysLib.rawwirte( .... ); // implement by yourself
	    pageTable[victimEntry].dirty = false;
	}
    }

    public Cache( int blockSize, int cacheBlocks ) {
	// instantiate pages
	// instantiate and initialize pageTable
    }

    public synchronized boolean read( int blockId, byte buffer[] ) {
	if ( blockId < 0 ) {
	    SysLib.cerr( "threadOS: a wrong blockId for cread\n" );
	    return false;
	}

	// locate a valid page
	for ( int i = 0; i < pageTable.length; i++ ) {
	    if ( pageTable[i].frame == blockId ) {
		// cache hit!!
		// copy pages[i] to buffer
		pageTable[i].reference = ture;
		return true;
	    }
	}

	// page miss!!
        // find an invalid page
	// if no invalid page is found, all pages are full
	//    seek for a victim
       	      int victimEntry = nextVictim( );

	// write back a dirty copy
	writeBack( victimEntry );
	// read a requested block from disk
	SysLib.rawread( .... );

	// cache it
	// copy pages[victimEntry] to buffer

	pageTable[victimEntry].frame = blockId;
        pageTable[victimEntry].reference = true;
	return true;
    }

    public synchronized boolean write( int blockId, byte buffer[] ) {
	if ( blockId < 0 ) {
	    SysLib.cerr( "threadOS: a wrong blockId for cwrite\n" );
	    return false;
	}

	// locate a valid page
	for ( int i = 0; i < pageTable.length; i++ ) {
	    if ( pageTable[i].frame == blockId ) {
		// cache hit
		// copy buffer to pages[i]
		pageTable[i].reference = true;
                pageTable[i].dirty = true;
		return true;
	    }
	}

	// page miss
        // find an invalid page
	// if no invalid page is found, all pages are full.
	//    seek for a victim
	      int victimEntry = nextVictim( );

	// write back a dirty copy
        writeBack( victimEntry );

	// cache it but not write through.
	// copy buffer to pages[victimEntry]
	pageTable[victimEntry].frame = blockId;
        pageTable[victimEntry].reference = true;
        pageTable[victimEntry].dirty = true;
	return true;
    }

    public synchronized void sync( ) {
	for ( int i = 0; i < pageTable.length; i++ )
	    writeBack( i );
	SysLib.sync( );
    }

    public synchronized void flush( ) {
	for ( int i = 0; i < pageTable.length; i++ ) {
	    writeBack( i );
	    pageTable[i].reference = false;
	    pageTable[i].frame = Entry.INVALID;
	}
	SysLib.sync( );
    }
}
