import java.util.*;

public class QueueNode {
    private Vector<Integer> tidQueue;

    public QueueNode( ) {
	tidQueue = new Vector<Integer>( );
	tidQueue.clear( );
    }

    public synchronized int sleep( ) {
	if ( tidQueue.size( ) == 0 ) {
	    try {
		wait( );
	    } catch ( InterruptedException e ) {
	    }
	}
	Integer pid = ( Integer )tidQueue.remove( 0 );
	return pid.intValue( );
    }

    public synchronized void wakeup( int pid ) {
	tidQueue.add( new Integer( pid ) );
	notify( );
    }
}
