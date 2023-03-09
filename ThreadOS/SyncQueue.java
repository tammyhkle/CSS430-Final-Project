/*
 * This is a template of SyncQueue.java. Chagne this file name into SyncQueue.java and
 * complete the implementation
 */
public class SyncQueue {
    private QueueNode queue[] = null;
    private final int COND_MAX = 10;
    private final int NO_TID = -1;

    public SyncQueue( ) {
	// You need to implement this constructor.
	// Assume SyncQueue( COND_MAX );
    }

    public SyncQueue( int condMax ) {
	// You need to implement this constructor.
    }

    int enqueueAndSleep( int condition ) {
	// Verify the correctness of condition.
	// Call the corresponding queue[ ].sleep( ).
	// Return the corresponding child thread ID.
    }

    void dequeueAndWakeup( int condition, int tid ) {
	// verify the correctness of condition.
	// Call the corresponding queue[ ].wakeup( ... );
    }

    void dequeueAndWakeup( int condition ) {
	// Assume tid = 0.
    }
}
