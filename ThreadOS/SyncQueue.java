public class SyncQueue {
    private QueueNode queue[] = null;
    private final int COND_MAX = 10;
    private final int NO_TID = -1;
    
    private void initQueue( int condMax ) {
	queue = new QueueNode[ condMax ];
	for ( int i = 0; i < condMax; i++ )
	    queue[i] = new QueueNode( );
    }

    public SyncQueue( ) {
	initQueue( COND_MAX );
    }

    public SyncQueue( int condMax ) {
	initQueue( condMax );
    }

    int enqueueAndSleep( int condition ) {
	if ( condition >= 0 && condition < queue.length )
	    return queue[ condition ].sleep( );
	else
	    return NO_TID;
    }

    void dequeueAndWakeup( int condition, int tid ) {
	if ( condition >= 0 && condition < queue.length )
	    queue[ condition ].wakeup( tid );
    }

    void dequeueAndWakeup( int condition ) {
	dequeueAndWakeup( condition, 0 );
    }
}
