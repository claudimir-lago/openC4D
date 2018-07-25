package infra;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import jlaia.Geral;
import serialSupport.SerineSerial;
import serineDispatcher.VirtualDevice;
import jlaia.NumString;
import jlaia.SimpleFileWriter;

/**
 * For two openC4D
 *
 * @author Claudimir Lucio do Lago
 */
public class MyDevice {

    public enum OptionTimeBase {
        MILLISECOND, SECOND, MINUTE, HOUR
    }

    // standard stuffs for virtual devices
    SerineSerial serialPort;
    Master master;
    String myID;
    String myIdentification;
    String lastMessage = "";
    boolean inTouch = false;
    String prefix;
    String engagedWith = "";
    // stuffs for the C4D
    private boolean blockA = false;
    private String runningMode = "";
    public BufferC4D buffer;
    private final DecimalFormat formatador = new DecimalFormat();
    private double factorTime = 1.0;
    private char separator = '\t';
    private final SimpleFileWriter arq;
    private final SimpleFileWriter arqBackup;
    private String suffix = ".dat";
    private String prefixArq = "";
    private String prefixFileName = "";
    private String fileName = "";
    private String fullArqName = "";
    private String prefixBackup = "";
    private boolean mustBackup = false;
    private boolean running = false;
    private boolean waitingForStart = false;
    private boolean waitingForStop = false;
    private boolean shouldSave = false;
    private boolean save1st = true;
    private boolean save2nd = true;

    /**
     * Just to see what is been sent to the serial. It is helpful to try to
     * identify if a Serine virtual device is attached to the port.
     *
     * @return the last message receive since the last request
     */
    public String getLastMessage() {
        String message = lastMessage;
        lastMessage = "";
        return message;
    }

    /**
     * @return true if the serial communication is OK
     */
    public boolean isSerialConnected() {
        return serialPort.isConnected();
    }

    /**
     * Opens a serial port
     *
     * @param portName a valid port name
     * @param baudeRate a valid baude rate
     * @return true if the serial port is opened
     */
    public boolean open(String portName, int baudeRate) {
        if (serialPort.isConnected()) {
            serialPort.close();
        }
        return serialPort.connect(portName, baudeRate);
    }

    /**
     * Constructor
     *
     * @param id_for_the_master a character valid to identify the master
     * @param permanentIdentification the Serine permanent identification of the
     * @param blockA True if detectors are in block A, or false if they are in
     * block B master
     */
    public MyDevice(char id_for_the_master, String permanentIdentification, boolean blockA) {
        this.buffer = new BufferC4D(100);
        master = new Master(id_for_the_master, permanentIdentification);
        serialPort = new SerineSerial();
        serialPort.setAddressed(master);
        prefix = "d" + myID;
        setC4DblockA(blockA);
        halt();
        arq = new SimpleFileWriter();
        arqBackup = new SimpleFileWriter();
    }

    /**
     * Set the place of the C4Ds
     *
     * @param blockA true for block A, or false for block B
     */
    public final void setC4DblockA(boolean blockA) {
        this.blockA = blockA;
    }

    /**
     * Not only close the serial port, but it also runs the disengage method.
     */
    public void close() {
        disengage();
        serialPort.close();
    }

    /**
     * Send a Serine message. The syntax is not checked.
     *
     * @param message a string containing a valid Serine message
     */
    public void send(String message) {
        if (serialPort.isConnected()) {
            serialPort.postHere(message);
        } else {
            System.out.println("The serial port is not connected.");
        }
    }

    /**
     * Start data sending
     *
     * @param mode "r" - continuous mode, "w" - wait for the external start, "t"
     * - wait for external start and stop
     */
    public void run(String mode) {
        if (blockA) {
            send(prefix + "Sf11100;");
        } else {
            send(prefix + "Sf10011;");
        }
        runningMode = mode;
        send(prefix + "G" + runningMode + ";");
    }

    /**
     * Sends a halt message to the detector
     */
    public final void halt() {
        send(prefix + "Gh;");
    }

    /**
     * Ask for just one point from the detector.
     */
    public final void getOnce() {
        send(prefix + "G;");
    }

    /**
     * Zeroes the chronometer of the detector.
     */
    public final void resetChronometer() {
        send(prefix + "Z;");
    }

    /**
     * Essentially, this class allows the communication with the other virtual
     * devices as well as the message processing for the master
     */
    public class Master extends VirtualDevice {

        private Master(char master_ID, String master_Identification) {
            super(master_ID);
            myID = String.valueOf(master_ID);
            myIdentification = master_Identification;
        }

        private void savePoint(double time, int ad0, int ad1) {
            if (!arq.isOpen()) {
                String now = Geral.timeStamp("d");
                fileName = prefixFileName + now + suffix;
                fullArqName = prefixArq + fileName;
                arq.open(fullArqName);
                if (arq.isOpen() && mustBackup) {
                    arqBackup.open(prefixBackup + "BK_" + prefixFileName + now + suffix);
                }
            }
            String linha = "";
            linha = linha + formatador.format(time);
            if (save1st) {
                linha = linha + separator + ad0;
            }
            if (save2nd) {
                linha = linha + separator + ad1;
            }
            arq.writeln(linha);
            if (arqBackup.isOpen()) {
                arqBackup.writeln(linha);
            }
        }

        /**
         * Runs the message processor.
         */
        @Override
        public void processMessageToMe() {
            lastMessage = messageString;
            switch (messageChar[2]) {
                case 'g':
                    switch (messageChar[3]) {
                        case 'S':
                            running = messageChar[4] == 'T';
                            waitingForStart = messageChar[5] == 'T';
                            waitingForStop = messageChar[6] == 'T';
                            if (shouldSave && isRunFinished()) {
                                stopDataAcquisition();
                            }
                            break;
                        default:
                            double time = factorTime * NumString.get(4, 10, messageString);
                            int ad0 = NumString.getInt(11, 17, messageString) - 2097152;
                            int ad1 = NumString.getInt(18, 24, messageString) - 2097152;
                            buffer.putPoint(time, ad0, ad1);
                            if (shouldSave) {
                                savePoint(time, ad0, ad1);
                            }
                            break;
                    }
                    break;
                case 'i':
                    engagedWith = String.copyValueOf(messageChar, 3, messageChar.length - 4);
                    System.out.println("engaged with " + engagedWith);
                    break;
                case 'x':
                    inTouch = (messageChar[3] == 'N');
                    if (inTouch) {
                        send(prefix + "I;");
                    } else {
                        engagedWith = "";
                    }
                    ;
                    break;
            }
        }

        /**
         * Allows processing the received broadcast messages.
         */
        @Override
        public void processBroadcastMessage() {
            lastMessage = messageString;
            switch (messageChar[2]) {
                case 'x':
                    break;
            }
        }

    ;

    }

    /**
     * This method should be populated with messages exchange and other
     * stuffs to assure that master and slave had agreed to communicate with
     * each other. This is not about the serial communication. This is the 
     * contrary of the disengage method.
     */
    public void engage() {
        send(prefix + "XN;");
    }

    /**
     * This method should be populated with messages exchange and other stuffs
     * to assure that master and slave lose connection without problems. This is
     * the contrary of the engage method.
     */
    public void disengage() {
        send(prefix + "XF;");
    }

    /**
     * Inform whether master and slave are in touch
     *
     * @return true if master and slave are committed
     */
    public boolean isEngaged() {
        return inTouch;
    }

    /**
     * Inform the identification of the device
     *
     * @return a string with the id given by the device
     */
    public String getEngagedWith() {
        return engagedWith;
    }

    /**
     * Select the time base for the data from the detector
     *
     * @param op the option according OptionTimeBase
     * @param decimalPoint The symbol to be used as the decimal separator.
     */
    public void setTimeBase(OptionTimeBase op, char decimalPoint) {
        DecimalFormatSymbols setDec = new DecimalFormatSymbols();
        setDec.setDecimalSeparator(decimalPoint);
        formatador.setDecimalFormatSymbols(setDec);
        if (null != op) {
            switch (op) {
                case MILLISECOND:
                    factorTime = 1.0;
                    formatador.applyPattern("#");
                    break;
                case SECOND:
                    factorTime = 1.0 / 1000.0;
                    formatador.applyPattern("0.000");
                    break;
                case MINUTE:
                    factorTime = 1.0 / 60000.0;
                    formatador.applyPattern("0.00000");
                    break;
                case HOUR:
                    factorTime = 1.0 / 3600000.0;
                    formatador.applyPattern("0.0000000");
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Set the patter for the data files
     *
     * @param prefix the starting portion of the filename
     * @param suffix the filename extension (e.g. ".dat")
     * @param prefixBackup the starting portion of the backup filename. If it is
     * empty (""), no backup file will be created.
     */
    public void setComplement(String prefix, String suffix, String prefixBackup) {
        this.prefixArq = prefix;
        if (!suffix.startsWith(".")) {
            suffix = "." + suffix;
        }
        this.suffix = suffix;
        this.prefixBackup = prefixBackup;
        mustBackup = (prefixBackup.length() > 0);
    }

    /**
     * Set the character to be used as columns separator in the data acquisition
     * data file.
     *
     * @param separator
     */
    public void setSeparator(char separator) {
        this.separator = separator;
    }

    /**
     * 
     * @return the beginning of the filename
     */
    public String getPrefixArq() {
        return prefixArq;
    }

    /**
     * 
     * @return the beginning of the backup filename
     */
    public String getPrefixBackup() {
        return prefixBackup;
    }

    /**
     * 
     * @return the end of the filename (".XXX")
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * 
     * @return the complete filename
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 
     * @return the filename and place where it is.
     */
    public String getFullArqName() {
        return fullArqName;
    }

    /**
     * 
     * @return true if an electropherogram is being acquired
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 
     * @return true if the device is waiting for a start pulse
     */
    public boolean isWaitingForStart() {
        return waitingForStart;
    }

    /**
     * 
     * @return true if the device is waiting for a stop pulse
     */
    public boolean isWaitingForStop() {
        return waitingForStop;
    }

    /**
     * 
     * @return true if the run is over.
     */
    public boolean isRunFinished() {
        return !(waitingForStart || running);
    }

    /**
     * 
     * @return true if the acquired data should be saved in a file
     */
    public boolean isShouldSave() {
        return shouldSave;
    }

    /**
     * Start data acquisition and save in a file according previous instructions
     * and with a timestamp of the moment when the acquisition was started.
     *
     * @param mode "r" - continuous mode, "w" - wait for the external start, "t"
     * - wait for external start and stop
     * @param prefixFileName a valid name for a file. If it is "", the filename
     * will be only the timestamp.
     * @param save1stC4D true to save the signal of the 1st detector
     * @param save2ndC4D true to save the signal of the 2nd detector
     */
    public void startDataAcquisition(String mode, String prefixFileName, boolean save1stC4D, boolean save2ndC4D) {
        if (prefixFileName.length() > 0) {
            this.prefixFileName = prefixFileName;
            shouldSave = true;
            save1st = save1stC4D;
            save2nd = save2ndC4D;
        }
        resetChronometer();
        run(mode);
    }

    /**
     *
     * @return true if data and backup files are closed
     */
    public boolean areFilesClosed() {
        return !(arq.isOpen() || arqBackup.isOpen());
    }

    /**
     * Finish the data acquisition and close the files.
     */
    public void stopDataAcquisition() {
        shouldSave = false;
        halt();
        arq.close();
        if (arq.isOpen()) {
            arq.close();
        }
        if (arqBackup.isOpen()) {
            arqBackup.close();
        }
        fileName = "";
    }
}
