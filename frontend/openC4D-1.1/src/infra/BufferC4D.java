package infra;

/**
 * Allows creating a cyclic buffer with points from the C4D detectors
 * 
 * @author Claudimir Lucio do Lago
 */
public class BufferC4D {

    public class DataPoint {
        public double time;
        public int det0;
        public int det1;
    };

    private final DataPoint[] buffer;
    private final int bufferSize;
    private int freePosIndex;
    private int nextNumberIndex;

    /**
     * Constructor
     * 
     * @param bufferSize the maximum number of points in the buffer
     */
    public BufferC4D(int bufferSize) {
        this.bufferSize = bufferSize;
        buffer = new DataPoint[bufferSize];
        freePosIndex = 0;
        nextNumberIndex = 0;
    }

    /**
     * Allows putting a point (time and ADC readings) in the buffer.
     * 
     * @param time the time corresponding to the ADC values
     * @param ad0 one ADC value
     * @param ad1 other ADC value
     */
    public void putPoint(double time, int ad0, int ad1) {
        buffer[freePosIndex] = new DataPoint();
        buffer[freePosIndex].time = time;
        buffer[freePosIndex].det0 = ad0;
        buffer[freePosIndex].det1 = ad1;
        freePosIndex = (freePosIndex + 1) % bufferSize;
    }

    /**
     * 
     * @return the number of points in the buffer
     */
    public int number() {
        int delta = freePosIndex - nextNumberIndex;
        if (delta < 0) {
            delta += bufferSize;
        }
        return delta;
    }

    /**
     * 
     * @return the next available point
     */
    public DataPoint nextPoint() {
        DataPoint agora = new DataPoint();
        if (number() > 0) {
            agora = buffer[nextNumberIndex];
            nextNumberIndex = (nextNumberIndex + 1) % bufferSize;
        }
        return agora;
    }

    /**
     * Clear the buffer.
     */
    public void reset() {
        nextNumberIndex = freePosIndex;
    }
}
