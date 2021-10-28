package swingFrontEnd;

import infra.BufferC4D;
import infra.Parameters;
import infra.MyDevice;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.Timer;
import javax.swing.UIManager;
import jssc.SerialPortList;
import javax.swing.JFileChooser;
import jlaia.NumString;
import jlaia.SimpleFileWriter;
import swingFrontEnd.graphUtil.XYGraph;
import swingFrontEnd.graphUtil.XYGraph2;

/**
 *
 * @author Claudimir Lucio do Lago
 */
public class FrontEnd extends javax.swing.JFrame {

    enum ExperimentStage {
        IDLE, PRE_RUN, RUN, POST_RUN
    }

    MyDevice myDevice;
    Parameters par = new Parameters();
    String[] baudRate = {"110", "150", "300", "1200", "2400", "4800", "9600",
        "19200", "38400", "57600", "115200", "230400", "460800", "921600"};
    private XYGraph graph1st = new XYGraph("1st C4D", "time  /  min", 100000);
    private XYGraph graph2nd = new XYGraph("2nd C4D", "time  /  min", 100000);
    private XYGraph2 graphAdj = new XYGraph2("C4D", "adjusted time  /  min", 100000);
    String fileName = "";
    ExperimentStage phase = ExperimentStage.IDLE;
    boolean OverAndOver = false;
    double fAdjT_1 = 1.0;
    double fAdjT_2 = 1.0;
    int sumBaseline = 0;
    int numBaseline = 0;
    int baseline = 0;

    private void initMyDevice() {
        jComboBox_port.setModel(new javax.swing.DefaultComboBoxModel(SerialPortList.getPortNames()));
        jComboBox_baudRate.setModel(new javax.swing.DefaultComboBoxModel(baudRate));
        jPanel_This_Port.setVisible(false);
        jPanel_just_Test.setVisible(false);
        par = Parameters.retrieve("last.par");
        jComboBox_port.setSelectedItem(par.portaSerial);
        jComboBox_baudRate.setSelectedItem(String.valueOf(par.baudRate));
        myDevice = new MyDevice('m', "t_masterC4D", true);
        jLabel_status.setText("disconnected   |   not engaded");
        jLabel_directory.setText(par.directory);
        jLabel_directoryBackup.setText(par.directoryBackup);
        jTextField_Prefix.setText(par.prefixo);
        jComboBox_complement.setSelectedItem(par.sufixo);
        myDevice.setComplement(par.directory, par.sufixo, par.directoryBackup);
        myDevice.setTimeBase(MyDevice.OptionTimeBase.MINUTE, '.');
        jRadioButton_wait_for_Start.setSelected((par.runningMode.equals("w")) || (par.runningMode.equals("t")));
        jRadioButton_wait_for_StartStop.setSelected(par.runningMode.equals("t"));
        jRadioButton_save1st.setSelected(par.save1stC4D);
        jRadioButton_save2nd.setSelected(par.save2ndC4D);
        jTextField_fileName.setEditable(false);
        jTextField_MaxTime.setText(NumString.format(par.maxRunningTime, 6, 2));
        jTextField_CapillaryLength.setText(NumString.format(par.capillaryLength, 6, 1));
        jTextField_InnerDiam.setText(NumString.format(par.capillaryID, 6, 1));
        jTextField_Voltage.setText(NumString.format(par.voltage, 6, 2));
        jTextField_BGEconductivity.setText(String.valueOf(par.bgeConductivity));
        recalcCurrent();
        jTextField_tMfirst.setText(NumString.format(par.tMfirst, 8, 3));
        jTextField_tMsecond.setText(NumString.format(par.tMsecond, 8, 3));
        jTextField_tMref.setText(NumString.format(par.tMref, 8, 3));
        fAdjT_1 = par.tMref / par.tMfirst;
        fAdjT_2 = par.tMref / par.tMsecond;
    }

    javax.swing.Action updateEach300ms;

    public FrontEnd() {

        updateEach300ms = new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (myDevice.isSerialConnected()) {
                    String message = myDevice.getLastMessage();
                    if (!message.contentEquals("")) {
                        jLabel_from_the_port.setText(message);
                    }
                    if (myDevice.isEngaged()) {
                        if ((phase == ExperimentStage.PRE_RUN) && !myDevice.isRunFinished()) {
                            phase = ExperimentStage.RUN;
                        }
                        if (phase == ExperimentStage.RUN) {
                            BufferC4D.DataPoint point;
                            boolean canGet = true;
                            while ((myDevice.buffer.number() > 0) && canGet) {
                                point = myDevice.buffer.nextPoint();
                                graph1st.letUpdate(jRadioButton_autoScale_1.isSelected());
                                graph1st.addPoint(point.time, point.det0, jRadioButton_autoScale_1.isSelected());
                                graph2nd.letUpdate(jRadioButton_autoScale_2.isSelected());
                                graph2nd.addPoint(point.time, point.det1, jRadioButton_autoScale_2.isSelected());
                                numBaseline += 1;
                                sumBaseline += point.det1 - point.det0;
                                graphAdj.letUpdate(jRadioButton_autoScale_Adj.isSelected());
                                graphAdj.addPoint1(fAdjT_1 * point.time, point.det0 + baseline + 100, jRadioButton_autoScale_Adj.isSelected());
                                graphAdj.addPoint2(fAdjT_2 * point.time, point.det1 - baseline, jRadioButton_autoScale_Adj.isSelected());
                                canGet = point.time < par.maxRunningTime;
                            }
                            if (!canGet || myDevice.isRunFinished()) {
                                stopRun();
                            }
                        }
                        if ((phase == ExperimentStage.POST_RUN) && myDevice.areFilesClosed()) {
                            if (OverAndOver) {
                                startRun();
                            } else {
                                phase = ExperimentStage.IDLE;
                            }
                        }
                        jLabel_Phase.setText(phase.toString());
                        jLabel_continuousMode.setText(myDevice.isRunning() ? "acquiring..." : "idle");
                        String waiting = "ready";
                        if (myDevice.isWaitingForStop()) {
                            waiting = "waiting for a stop pulse";
                        }
                        if (myDevice.isWaitingForStart()) {
                            waiting = "waiting for a start pulse";
                        }
                        jLabel_waitingPulse.setText(waiting);
                        jTextField_fileName.setText(myDevice.getFileName());
                        jLabel_areFilesClosed.setText(myDevice.areFilesClosed() ? "closed" : "open");
                        jButton_save_Report.setEnabled(!myDevice.areFilesClosed());
                    }
                }
            }
        };

        initComponents();
        CleanUpAdapter cleaner = new CleanUpAdapter();
        this.addWindowListener(cleaner);

        jPanel_MonitorA.add(graph1st, BorderLayout.CENTER);
        graph1st.setBounds(jPanel_mold1st.getBounds());
        graph1st.setVisible(true);
        graph1st.setFactorXAxis(1.1);

        jPanel_MonitorA.add(graph2nd, BorderLayout.CENTER);
        graph2nd.setBounds(jPanel_mold2nd.getBounds());
        graph2nd.setVisible(true);
        graph2nd.setFactorXAxis(1.1);

        jPanel_MonitorB.add(graphAdj, BorderLayout.CENTER);
        graphAdj.setBounds(jPanel_moldAdj.getBounds());
        graphAdj.setVisible(true);
        graphAdj.setFactorXAxis(1.1);

        jTabbedPane_Input.setEnabled(false); //Disable everything, but the connection tab
        jTabbedPane_Monitor.setEnabled(false);

        initMyDevice();

        new Timer(300, updateEach300ms).start();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jFileChooser_directory = new javax.swing.JFileChooser();
        buttonGroup_runningModeDA = new javax.swing.ButtonGroup();
        PanelStatus = new javax.swing.JPanel();
        jLabel_status = new javax.swing.JLabel();
        jLabel_continuousMode = new javax.swing.JLabel();
        jLabel_waitingPulse = new javax.swing.JLabel();
        jLabel_Phase = new javax.swing.JLabel();
        jTabbedPane_Input = new javax.swing.JTabbedPane();
        jPanel_Connection = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jComboBox_port = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jComboBox_baudRate = new javax.swing.JComboBox();
        jButton_ConectSerial = new javax.swing.JButton();
        jPanel_This_Port = new javax.swing.JPanel();
        jTextField_command_line = new javax.swing.JTextField();
        jButton_send_a_message = new javax.swing.JButton();
        jLabel_title_from_the_serial = new javax.swing.JLabel();
        jLabel_from_the_port = new javax.swing.JLabel();
        jButton_WhoIsThere = new javax.swing.JButton();
        jButton_Accept = new javax.swing.JButton();
        jPanel_just_Test = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jButton_once = new javax.swing.JButton();
        jButton_halt = new javax.swing.JButton();
        jButton_Zero = new javax.swing.JButton();
        jButton_rescan = new javax.swing.JButton();
        jPanel_Conditions = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        jTextField_Prefix = new javax.swing.JTextField();
        jButton_chooseDirectory = new javax.swing.JButton();
        jLabel_directory = new javax.swing.JLabel();
        jButton_chooseDirectoryBackup = new javax.swing.JButton();
        jLabel_directoryBackup = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        jComboBox_complement = new javax.swing.JComboBox();
        jLabel32 = new javax.swing.JLabel();
        jComboBox_separator = new javax.swing.JComboBox();
        jRadioButton_save1st = new javax.swing.JRadioButton();
        jRadioButton_save2nd = new javax.swing.JRadioButton();
        jLabel3 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jRadioButton_wait_for_Start = new javax.swing.JRadioButton();
        jRadioButton_wait_for_StartStop = new javax.swing.JRadioButton();
        jRadioButton_wait_None = new javax.swing.JRadioButton();
        jRadioButton_ContinuousAcquisition = new javax.swing.JRadioButton();
        jPanel_Graph = new javax.swing.JPanel();
        jToggleButton_StartExperiment = new javax.swing.JToggleButton();
        jLabel_directory1 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jTextField_fileName = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel_areFilesClosed = new javax.swing.JLabel();
        jToggleButton_AbortRun = new javax.swing.JToggleButton();
        jPanel5 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jTextField_CapillaryLength = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jTextField_Voltage = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jTextField_BGEconductivity = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jTextField_InnerDiam = new javax.swing.JTextField();
        jLabel22 = new javax.swing.JLabel();
        jLabel_Expected_Current = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextPane_Comments = new javax.swing.JTextPane();
        jButton_save_Report = new javax.swing.JButton();
        jCheckBox_saveElectrophoreticConditions = new javax.swing.JCheckBox();
        jLabel17 = new javax.swing.JLabel();
        jTabbedPane_Monitor = new javax.swing.JTabbedPane();
        jPanel_MonitorA = new javax.swing.JPanel();
        jPanel_mold1st = new javax.swing.JPanel();
        jPanel_mold2nd = new javax.swing.JPanel();
        jRadioButton_autoScale_1 = new javax.swing.JRadioButton();
        jRadioButton_autoScale_2 = new javax.swing.JRadioButton();
        jPanel_MonitorB = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        jTextField_tMfirst = new javax.swing.JTextField();
        jLabel23 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jTextField_tMsecond = new javax.swing.JTextField();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jTextField_tMref = new javax.swing.JTextField();
        jLabel26 = new javax.swing.JLabel();
        jButton_Update = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        jSeparator3 = new javax.swing.JSeparator();
        jPanel_moldAdj = new javax.swing.JPanel();
        jRadioButton_autoScale_Adj = new javax.swing.JRadioButton();
        jLabel10 = new javax.swing.JLabel();
        jTextField_MaxTime = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();

        jFileChooser_directory.setCurrentDirectory(new java.io.File("C:\\lixo"));
        jFileChooser_directory.setDialogTitle("Place to store the experiments");
        jFileChooser_directory.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("openC4D");
        setLocation(new java.awt.Point(200, 200));
        setResizable(false);

        PanelStatus.setBorder(javax.swing.BorderFactory.createTitledBorder("Status"));

        jLabel_status.setText("<none>");

        jLabel_continuousMode.setText("idle");

        jLabel_waitingPulse.setText("ready");

        jLabel_Phase.setText("idle");

        javax.swing.GroupLayout PanelStatusLayout = new javax.swing.GroupLayout(PanelStatus);
        PanelStatus.setLayout(PanelStatusLayout);
        PanelStatusLayout.setHorizontalGroup(
            PanelStatusLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanelStatusLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel_status, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel_continuousMode, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel_waitingPulse, javax.swing.GroupLayout.PREFERRED_SIZE, 168, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(30, 30, 30)
                .addComponent(jLabel_Phase, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(353, Short.MAX_VALUE))
        );
        PanelStatusLayout.setVerticalGroup(
            PanelStatusLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, PanelStatusLayout.createSequentialGroup()
                .addGap(0, 8, Short.MAX_VALUE)
                .addGroup(PanelStatusLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel_status)
                    .addComponent(jLabel_continuousMode)
                    .addComponent(jLabel_waitingPulse)
                    .addComponent(jLabel_Phase)))
        );

        jPanel_Connection.setToolTipText("");

        jLabel1.setText("select com port");

        jComboBox_port.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "com1", "com2" }));

        jLabel2.setText("baud rate");

        jComboBox_baudRate.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "9600", "115200" }));

        jButton_ConectSerial.setText("Connect");
        jButton_ConectSerial.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_ConectSerialActionPerformed(evt);
            }
        });

        jPanel_This_Port.setBorder(javax.swing.BorderFactory.createTitledBorder("This port"));
        jPanel_This_Port.setToolTipText("This port");
        jPanel_This_Port.setPreferredSize(new java.awt.Dimension(600, 160));

        jButton_send_a_message.setText("send the message:");
        jButton_send_a_message.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_send_a_messageActionPerformed(evt);
            }
        });

        jLabel_title_from_the_serial.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jLabel_title_from_the_serial.setText("message from the port:");

        jLabel_from_the_port.setBackground(new java.awt.Color(204, 204, 204));
        jLabel_from_the_port.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jLabel_from_the_port.setForeground(new java.awt.Color(0, 153, 153));
        jLabel_from_the_port.setText("<none>");

        jButton_WhoIsThere.setText("Who is there?");
        jButton_WhoIsThere.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_WhoIsThereActionPerformed(evt);
            }
        });

        jButton_Accept.setText("Engage with this port");
        jButton_Accept.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_AcceptActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel_This_PortLayout = new javax.swing.GroupLayout(jPanel_This_Port);
        jPanel_This_Port.setLayout(jPanel_This_PortLayout);
        jPanel_This_PortLayout.setHorizontalGroup(
            jPanel_This_PortLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_This_PortLayout.createSequentialGroup()
                .addGroup(jPanel_This_PortLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_This_PortLayout.createSequentialGroup()
                        .addGroup(jPanel_This_PortLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton_Accept)
                            .addGroup(jPanel_This_PortLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(jButton_WhoIsThere, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jButton_send_a_message, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(jLabel_from_the_port, javax.swing.GroupLayout.PREFERRED_SIZE, 336, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel_title_from_the_serial))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jTextField_command_line))
                .addContainerGap())
        );
        jPanel_This_PortLayout.setVerticalGroup(
            jPanel_This_PortLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_This_PortLayout.createSequentialGroup()
                .addComponent(jButton_Accept)
                .addGap(18, 18, 18)
                .addComponent(jLabel_title_from_the_serial)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel_from_the_port)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton_WhoIsThere)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton_send_a_message)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jTextField_command_line, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(319, 319, 319))
        );

        jPanel_just_Test.setBorder(javax.swing.BorderFactory.createTitledBorder("Just for test"));

        jPanel2.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jButton_once.setText("get");
        jButton_once.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_onceActionPerformed(evt);
            }
        });

        jButton_halt.setText("halt");
        jButton_halt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_haltActionPerformed(evt);
            }
        });

        jButton_Zero.setText("Chronometer reset");
        jButton_Zero.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_ZeroActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton_once)
                    .addComponent(jButton_halt)
                    .addComponent(jButton_Zero))
                .addContainerGap(87, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButton_once)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton_halt)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton_Zero)
                .addContainerGap(40, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel_just_TestLayout = new javax.swing.GroupLayout(jPanel_just_Test);
        jPanel_just_Test.setLayout(jPanel_just_TestLayout);
        jPanel_just_TestLayout.setHorizontalGroup(
            jPanel_just_TestLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_just_TestLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(92, Short.MAX_VALUE))
        );
        jPanel_just_TestLayout.setVerticalGroup(
            jPanel_just_TestLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_just_TestLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButton_rescan.setText("rescan");
        jButton_rescan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_rescanActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel_ConnectionLayout = new javax.swing.GroupLayout(jPanel_Connection);
        jPanel_Connection.setLayout(jPanel_ConnectionLayout);
        jPanel_ConnectionLayout.setHorizontalGroup(
            jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                .addGroup(jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel_just_Test, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel_This_Port, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 408, Short.MAX_VALUE))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel_ConnectionLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                        .addComponent(jButton_rescan)
                        .addGap(18, 18, 18)
                        .addComponent(jComboBox_port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(jLabel1)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(173, 173, 173))
                    .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                        .addComponent(jComboBox_baudRate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 90, Short.MAX_VALUE)
                        .addComponent(jButton_ConectSerial)
                        .addGap(44, 44, 44))))
        );
        jPanel_ConnectionLayout.setVerticalGroup(
            jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_ConnectionLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel_ConnectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox_port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jComboBox_baudRate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton_ConectSerial)
                    .addComponent(jButton_rescan))
                .addGap(38, 38, 38)
                .addComponent(jPanel_This_Port, javax.swing.GroupLayout.PREFERRED_SIZE, 197, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel_just_Test, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(116, Short.MAX_VALUE))
        );

        jTabbedPane_Input.addTab("connection", jPanel_Connection);

        jLabel4.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel4.setText("experimental conditions for the electrophoretic run");

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Files"));

        jLabel5.setText("prefix:");

        jTextField_Prefix.setText("Electropherogram_");
        jTextField_Prefix.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_PrefixFocusLost(evt);
            }
        });

        jButton_chooseDirectory.setText("directory");
        jButton_chooseDirectory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_chooseDirectoryActionPerformed(evt);
            }
        });

        jLabel_directory.setText("directory");

        jButton_chooseDirectoryBackup.setText("backup directory");
        jButton_chooseDirectoryBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_chooseDirectoryBackupActionPerformed(evt);
            }
        });

        jLabel_directoryBackup.setText("directory");

        jLabel34.setText("file complement:");

        jComboBox_complement.setModel(new javax.swing.DefaultComboBoxModel(new String[] { ".dat", ".txt", ".ele", "no complement" }));
        jComboBox_complement.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox_complementActionPerformed(evt);
            }
        });

        jLabel32.setText("column separator:");

        jComboBox_separator.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "space", "tab", "comma" }));
        jComboBox_separator.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox_separatorActionPerformed(evt);
            }
        });

        jRadioButton_save1st.setText("first C4D");
        jRadioButton_save1st.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_save1stActionPerformed(evt);
            }
        });

        jRadioButton_save2nd.setText("second C4D");
        jRadioButton_save2nd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_save2ndActionPerformed(evt);
            }
        });

        jLabel3.setText("Save signal from the:");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel_directoryBackup, javax.swing.GroupLayout.DEFAULT_SIZE, 386, Short.MAX_VALUE)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel34)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jComboBox_complement, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 30, Short.MAX_VALUE)
                            .addComponent(jLabel32)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jComboBox_separator, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGap(10, 10, 10)))
                    .addComponent(jButton_chooseDirectoryBackup)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextField_Prefix, javax.swing.GroupLayout.PREFERRED_SIZE, 274, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton_chooseDirectory)
                    .addComponent(jLabel_directory, javax.swing.GroupLayout.PREFERRED_SIZE, 386, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 20, Short.MAX_VALUE))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jRadioButton_save1st)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jRadioButton_save2nd)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField_Prefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton_chooseDirectory)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel_directory)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton_chooseDirectoryBackup)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel_directoryBackup)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox_complement, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel34)
                    .addComponent(jLabel32)
                    .addComponent(jComboBox_separator, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jRadioButton_save1st)
                    .addComponent(jRadioButton_save2nd)
                    .addComponent(jLabel3))
                .addContainerGap(24, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Running mode  (external control)"));

        buttonGroup_runningModeDA.add(jRadioButton_wait_for_Start);
        jRadioButton_wait_for_Start.setText("Wait for a start pulse");
        jRadioButton_wait_for_Start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_wait_for_StartActionPerformed(evt);
            }
        });

        buttonGroup_runningModeDA.add(jRadioButton_wait_for_StartStop);
        jRadioButton_wait_for_StartStop.setText("Wait for start and stop pulses");
        jRadioButton_wait_for_StartStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_wait_for_StartStopActionPerformed(evt);
            }
        });

        buttonGroup_runningModeDA.add(jRadioButton_wait_None);
        jRadioButton_wait_None.setSelected(true);
        jRadioButton_wait_None.setText("None");
        jRadioButton_wait_None.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_wait_NoneActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jRadioButton_wait_for_Start)
                    .addComponent(jRadioButton_wait_for_StartStop)
                    .addComponent(jRadioButton_wait_None))
                .addContainerGap(14, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jRadioButton_wait_for_Start)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jRadioButton_wait_for_StartStop)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jRadioButton_wait_None)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jRadioButton_ContinuousAcquisition.setText("continuous runs");
        jRadioButton_ContinuousAcquisition.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton_ContinuousAcquisitionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel_ConditionsLayout = new javax.swing.GroupLayout(jPanel_Conditions);
        jPanel_Conditions.setLayout(jPanel_ConditionsLayout);
        jPanel_ConditionsLayout.setHorizontalGroup(
            jPanel_ConditionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_ConditionsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_ConditionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jRadioButton_ContinuousAcquisition)
                    .addComponent(jLabel4)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel_ConditionsLayout.setVerticalGroup(
            jPanel_ConditionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_ConditionsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jRadioButton_ContinuousAcquisition)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(159, Short.MAX_VALUE))
        );

        jPanel3.getAccessibleContext().setAccessibleName("Running mode");

        jTabbedPane_Input.addTab("before the experiment", jPanel_Conditions);

        jToggleButton_StartExperiment.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        jToggleButton_StartExperiment.setText("Start Experiment");
        jToggleButton_StartExperiment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton_StartExperimentActionPerformed(evt);
            }
        });

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, jLabel_directory, org.jdesktop.beansbinding.ELProperty.create("${text}"), jLabel_directory1, org.jdesktop.beansbinding.BeanProperty.create("text"));
        bindingGroup.addBinding(binding);

        jLabel6.setText("Directory:");

        jLabel7.setText("Filename:");

        jLabel8.setText("prefix:");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, jTextField_Prefix, org.jdesktop.beansbinding.ELProperty.create("${text}"), jLabel9, org.jdesktop.beansbinding.BeanProperty.create("text"));
        bindingGroup.addBinding(binding);

        jLabel_areFilesClosed.setText("closed");

        jToggleButton_AbortRun.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        jToggleButton_AbortRun.setText("End this run");
        jToggleButton_AbortRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton_AbortRunActionPerformed(evt);
            }
        });

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Electrophoretic conditions"));

        jLabel12.setText("capillary length:");

        jLabel18.setText("cm");

        jTextField_CapillaryLength.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_CapillaryLength.setText("10.0");
        jTextField_CapillaryLength.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_CapillaryLengthFocusLost(evt);
            }
        });
        jTextField_CapillaryLength.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_CapillaryLengthActionPerformed(evt);
            }
        });

        jLabel15.setText("voltage:");

        jTextField_Voltage.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_Voltage.setText("30.00");
        jTextField_Voltage.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_VoltageFocusLost(evt);
            }
        });
        jTextField_Voltage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_VoltageActionPerformed(evt);
            }
        });

        jLabel19.setText("kV");

        jLabel16.setText("BGE conductivity:");

        jTextField_BGEconductivity.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_BGEconductivity.setText("4.74e-02");
        jTextField_BGEconductivity.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_BGEconductivityFocusLost(evt);
            }
        });
        jTextField_BGEconductivity.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_BGEconductivityActionPerformed(evt);
            }
        });

        jLabel20.setText("S/m");

        jLabel21.setText("capillary i.d.:");

        jTextField_InnerDiam.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_InnerDiam.setText("50");
        jTextField_InnerDiam.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_InnerDiamFocusLost(evt);
            }
        });
        jTextField_InnerDiam.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_InnerDiamActionPerformed(evt);
            }
        });

        jLabel22.setText("µm");

        jLabel_Expected_Current.setText("Expected current:");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel12, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel21, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel15, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel16, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(jTextField_BGEconductivity, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField_Voltage, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField_InnerDiam, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField_CapillaryLength, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel18)
                            .addComponent(jLabel22)
                            .addComponent(jLabel19)
                            .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel_Expected_Current)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(jTextField_CapillaryLength, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel18))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel21)
                    .addComponent(jTextField_InnerDiam, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(jTextField_Voltage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel19))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField_BGEconductivity, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16)
                    .addComponent(jLabel20))
                .addGap(18, 18, 18)
                .addComponent(jLabel_Expected_Current)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jTextPane_Comments);

        jButton_save_Report.setText("Save Report");
        jButton_save_Report.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_save_ReportActionPerformed(evt);
            }
        });

        jCheckBox_saveElectrophoreticConditions.setSelected(true);
        jCheckBox_saveElectrophoreticConditions.setText("Save Electrophoretic conditions");

        jLabel17.setText("Report:");

        javax.swing.GroupLayout jPanel_GraphLayout = new javax.swing.GroupLayout(jPanel_Graph);
        jPanel_Graph.setLayout(jPanel_GraphLayout);
        jPanel_GraphLayout.setHorizontalGroup(
            jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_GraphLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel_directory1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextField_fileName)
                    .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel_GraphLayout.createSequentialGroup()
                        .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addComponent(jButton_save_Report, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel_GraphLayout.createSequentialGroup()
                                .addComponent(jCheckBox_saveElectrophoreticConditions)
                                .addGap(0, 4, Short.MAX_VALUE)))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel_GraphLayout.createSequentialGroup()
                        .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel_GraphLayout.createSequentialGroup()
                                .addComponent(jToggleButton_StartExperiment, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jToggleButton_AbortRun))
                            .addComponent(jLabel6)
                            .addComponent(jLabel8)
                            .addGroup(jPanel_GraphLayout.createSequentialGroup()
                                .addComponent(jLabel7)
                                .addGap(222, 222, 222)
                                .addComponent(jLabel_areFilesClosed))
                            .addComponent(jLabel17))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel_GraphLayout.setVerticalGroup(
            jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_GraphLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jToggleButton_AbortRun, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel_GraphLayout.createSequentialGroup()
                        .addComponent(jToggleButton_StartExperiment, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(34, 34, 34)
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel_directory1)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addGap(18, 18, 18)
                .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(jLabel_areFilesClosed))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField_fileName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(15, 15, 15)
                .addComponent(jLabel17)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_GraphLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_GraphLayout.createSequentialGroup()
                        .addGap(41, 128, Short.MAX_VALUE)
                        .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel_GraphLayout.createSequentialGroup()
                        .addComponent(jScrollPane1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jCheckBox_saveElectrophoreticConditions)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton_save_Report)))
                .addContainerGap())
        );

        jTabbedPane_Input.addTab("experiment", jPanel_Graph);

        jPanel_mold1st.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel_mold1st.setEnabled(false);
        jPanel_mold1st.setOpaque(false);
        jPanel_mold1st.setPreferredSize(new java.awt.Dimension(400, 300));

        javax.swing.GroupLayout jPanel_mold1stLayout = new javax.swing.GroupLayout(jPanel_mold1st);
        jPanel_mold1st.setLayout(jPanel_mold1stLayout);
        jPanel_mold1stLayout.setHorizontalGroup(
            jPanel_mold1stLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 569, Short.MAX_VALUE)
        );
        jPanel_mold1stLayout.setVerticalGroup(
            jPanel_mold1stLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 294, Short.MAX_VALUE)
        );

        jPanel_mold2nd.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel_mold2nd.setEnabled(false);
        jPanel_mold2nd.setOpaque(false);
        jPanel_mold2nd.setPreferredSize(new java.awt.Dimension(400, 300));

        javax.swing.GroupLayout jPanel_mold2ndLayout = new javax.swing.GroupLayout(jPanel_mold2nd);
        jPanel_mold2nd.setLayout(jPanel_mold2ndLayout);
        jPanel_mold2ndLayout.setHorizontalGroup(
            jPanel_mold2ndLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 569, Short.MAX_VALUE)
        );
        jPanel_mold2ndLayout.setVerticalGroup(
            jPanel_mold2ndLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 294, Short.MAX_VALUE)
        );

        jRadioButton_autoScale_1.setSelected(true);
        jRadioButton_autoScale_1.setText("automatic time scale 1st C4D");

        jRadioButton_autoScale_2.setSelected(true);
        jRadioButton_autoScale_2.setText("automatic time scale 2nd C4D");

        javax.swing.GroupLayout jPanel_MonitorALayout = new javax.swing.GroupLayout(jPanel_MonitorA);
        jPanel_MonitorA.setLayout(jPanel_MonitorALayout);
        jPanel_MonitorALayout.setHorizontalGroup(
            jPanel_MonitorALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel_MonitorALayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_MonitorALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel_MonitorALayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jRadioButton_autoScale_2))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel_MonitorALayout.createSequentialGroup()
                        .addComponent(jRadioButton_autoScale_1)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel_mold1st, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE)
                    .addComponent(jPanel_mold2nd, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel_MonitorALayout.setVerticalGroup(
            jPanel_MonitorALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_MonitorALayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel_mold1st, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButton_autoScale_1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 2, Short.MAX_VALUE)
                .addComponent(jRadioButton_autoScale_2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel_mold2nd, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jTabbedPane_Monitor.addTab("monitor", jPanel_MonitorA);

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("marker"));

        jLabel13.setText("migration time at 1st C4D:");

        jTextField_tMfirst.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_tMfirst.setText("10.0");
        jTextField_tMfirst.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_tMfirstFocusLost(evt);
            }
        });

        jLabel23.setText("min");

        jLabel14.setText("migration time at 2nd C4D:");

        jTextField_tMsecond.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_tMsecond.setText("10.0");
        jTextField_tMsecond.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_tMsecondFocusLost(evt);
            }
        });

        jLabel24.setText("min");

        jLabel25.setText("reference migration time:");

        jTextField_tMref.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_tMref.setText("10.0");
        jTextField_tMref.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_tMrefFocusLost(evt);
            }
        });

        jLabel26.setText("min");

        jButton_Update.setText("update");
        jButton_Update.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_UpdateActionPerformed(evt);
            }
        });

        jSeparator2.setBackground(new java.awt.Color(0, 0, 255));
        jSeparator2.setForeground(new java.awt.Color(0, 0, 255));

        jSeparator3.setBackground(new java.awt.Color(255, 0, 0));
        jSeparator3.setForeground(new java.awt.Color(255, 0, 0));

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel25, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel14, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel13, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jButton_Update)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField_tMref, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField_tMsecond, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField_tMfirst, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel23)
                                    .addComponent(jLabel24))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jSeparator2, javax.swing.GroupLayout.DEFAULT_SIZE, 50, Short.MAX_VALUE)
                                    .addComponent(jSeparator3))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jLabel26)
                                .addContainerGap(67, Short.MAX_VALUE))))))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel13)
                    .addComponent(jTextField_tMfirst, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel23)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel14)
                    .addComponent(jTextField_tMsecond, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24)
                    .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel25)
                    .addComponent(jTextField_tMref, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel26))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton_Update)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel_moldAdj.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel_moldAdj.setEnabled(false);
        jPanel_moldAdj.setOpaque(false);
        jPanel_moldAdj.setPreferredSize(new java.awt.Dimension(400, 300));

        javax.swing.GroupLayout jPanel_moldAdjLayout = new javax.swing.GroupLayout(jPanel_moldAdj);
        jPanel_moldAdj.setLayout(jPanel_moldAdjLayout);
        jPanel_moldAdjLayout.setHorizontalGroup(
            jPanel_moldAdjLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel_moldAdjLayout.setVerticalGroup(
            jPanel_moldAdjLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 366, Short.MAX_VALUE)
        );

        jRadioButton_autoScale_Adj.setSelected(true);
        jRadioButton_autoScale_Adj.setText("automatic time scale");

        javax.swing.GroupLayout jPanel_MonitorBLayout = new javax.swing.GroupLayout(jPanel_MonitorB);
        jPanel_MonitorB.setLayout(jPanel_MonitorBLayout);
        jPanel_MonitorBLayout.setHorizontalGroup(
            jPanel_MonitorBLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_MonitorBLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_MonitorBLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel_moldAdj, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE)
                    .addGroup(jPanel_MonitorBLayout.createSequentialGroup()
                        .addGroup(jPanel_MonitorBLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jRadioButton_autoScale_Adj))
                        .addGap(0, 283, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel_MonitorBLayout.setVerticalGroup(
            jPanel_MonitorBLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel_MonitorBLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel_moldAdj, javax.swing.GroupLayout.DEFAULT_SIZE, 372, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(jRadioButton_autoScale_Adj)
                .addGap(26, 26, 26)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(79, 79, 79))
        );

        jTabbedPane_Monitor.addTab("adv. mon.", jPanel_MonitorB);

        jLabel10.setText("maximum electropherogram time:");

        jTextField_MaxTime.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        jTextField_MaxTime.setText("10.0");
        jTextField_MaxTime.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField_MaxTimeFocusLost(evt);
            }
        });
        jTextField_MaxTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_MaxTimeActionPerformed(evt);
            }
        });

        jLabel11.setText("min");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(PanelStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTabbedPane_Input, javax.swing.GroupLayout.PREFERRED_SIZE, 423, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel10)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jTextField_MaxTime, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel11)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTabbedPane_Monitor, javax.swing.GroupLayout.PREFERRED_SIZE, 600, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jTabbedPane_Input, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jTextField_MaxTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel11))
                            .addComponent(jLabel10)))
                    .addComponent(jTabbedPane_Monitor, javax.swing.GroupLayout.PREFERRED_SIZE, 702, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(PanelStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        bindingGroup.bind();

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton_ConectSerialActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_ConectSerialActionPerformed
        if (myDevice.isSerialConnected()) {
            myDevice.close();
            jButton_ConectSerial.setText("Connect");
            jButton_Accept.setEnabled(true);
            jLabel_from_the_port.setText("");
            jPanel_This_Port.setVisible(false);
            jPanel_just_Test.setVisible(false);
            jTabbedPane_Input.setEnabled(false);
            jTabbedPane_Monitor.setEnabled(false);
            jLabel_status.setText("disconnected   |   not engaded");
        } else {
            par.portaSerial = jComboBox_port.getSelectedItem().toString();
            par.baudRate = Integer.parseInt(jComboBox_baudRate.getSelectedItem().toString());
            jButton_ConectSerial.setText("Disconnect");
            jPanel_This_Port.setVisible(myDevice.open(par.portaSerial, par.baudRate));
            jLabel_status.setText("connected   |   not engaded");
        }
    }//GEN-LAST:event_jButton_ConectSerialActionPerformed

    private void jButton_ZeroActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_ZeroActionPerformed
        myDevice.resetChronometer();
    }//GEN-LAST:event_jButton_ZeroActionPerformed

    private void jButton_onceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_onceActionPerformed
        myDevice.halt();
        myDevice.getOnce();
    }//GEN-LAST:event_jButton_onceActionPerformed

    private void jButton_haltActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_haltActionPerformed
        myDevice.halt();
    }//GEN-LAST:event_jButton_haltActionPerformed

    private void jButton_chooseDirectoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_chooseDirectoryActionPerformed
        if (!par.directory.equals("")) {
            jFileChooser_directory.setCurrentDirectory(new File(par.directory));
        }
        int retorno = jFileChooser_directory.showOpenDialog(this);
        if (retorno == JFileChooser.APPROVE_OPTION) {
            par.directory = jFileChooser_directory.getSelectedFile().toString() + "\\";
            jLabel_directory.setText(par.directory);
            if (par.directoryBackup.equals("")) {
                par.directoryBackup = par.directory;
                jLabel_directoryBackup.setText(par.directoryBackup);
            }
            myDevice.setComplement(par.directory, par.sufixo, par.directoryBackup);
        }
    }//GEN-LAST:event_jButton_chooseDirectoryActionPerformed

    private void jButton_chooseDirectoryBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_chooseDirectoryBackupActionPerformed
        if (!par.directoryBackup.equals("")) {
            jFileChooser_directory.setCurrentDirectory(new File(par.directoryBackup));
        }
        int retorno = jFileChooser_directory.showOpenDialog(this);
        if (retorno == JFileChooser.APPROVE_OPTION) {
            par.directoryBackup = jFileChooser_directory.getSelectedFile().toString() + "\\";
            jLabel_directoryBackup.setText(par.directoryBackup);
            myDevice.setComplement(par.directory, par.sufixo, par.directoryBackup);
        }
    }//GEN-LAST:event_jButton_chooseDirectoryBackupActionPerformed

    private void jComboBox_complementActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox_complementActionPerformed
        par.sufixo = jComboBox_complement.getSelectedItem().toString();
        if (par.sufixo.equals("no complement")) {
            par.sufixo = "";
        }
        myDevice.setComplement(par.directory, par.sufixo, par.directoryBackup);
    }//GEN-LAST:event_jComboBox_complementActionPerformed

    private void jTextField_PrefixFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_PrefixFocusLost
        par.prefixo = jTextField_Prefix.getText();
    }//GEN-LAST:event_jTextField_PrefixFocusLost

    private void jComboBox_separatorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox_separatorActionPerformed
        String c = jComboBox_separator.getSelectedItem().toString();
        switch (c) {
            case "space":
                myDevice.setSeparator(' ');
                break;
            case "tab":
                myDevice.setSeparator((char) 9);
                break;
            case "comma":
                myDevice.setSeparator(',');
                break;
            default:
                break;
        }
    }//GEN-LAST:event_jComboBox_separatorActionPerformed

    private void startRun() {
        updateEach300ms.setEnabled(false);
        phase = ExperimentStage.PRE_RUN;
        myDevice.buffer.reset();
        graph1st.removeAllPoints();
        graph2nd.removeAllPoints();
        graphAdj.removeAllPoints();
        sumBaseline = 0;
        numBaseline = 0;
        graph1st.renderer.setSeriesPaint(0, Color.BLUE);
        graph2nd.renderer.setSeriesPaint(0, Color.RED);
        graphAdj.renderer.setSeriesPaint(0, Color.BLUE);
        graphAdj.renderer.setSeriesPaint(1, Color.RED);
        if (jRadioButton_wait_for_Start.isSelected()) {
            par.runningMode = "w";
        } else if (jRadioButton_wait_for_StartStop.isSelected()) {
            par.runningMode = "t";
        } else {
            par.runningMode = "r";
        }
        myDevice.startDataAcquisition(par.runningMode, par.prefixo, par.save1stC4D, par.save2ndC4D);
        updateEach300ms.setEnabled(true);
    }

    private void stopRun() {
        updateEach300ms.setEnabled(false);
        phase = ExperimentStage.POST_RUN;
        myDevice.stopDataAcquisition();
        graph1st.renderer.setSeriesPaint(0, Color.DARK_GRAY);
        graph2nd.renderer.setSeriesPaint(0, Color.DARK_GRAY);
        graphAdj.renderer.setSeriesPaint(0, Color.CYAN);
        graphAdj.renderer.setSeriesPaint(1, Color.MAGENTA);
        myDevice.buffer.reset();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Logger.getLogger(FrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        }
        updateEach300ms.setEnabled(true);
    }

    private void jToggleButton_StartExperimentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton_StartExperimentActionPerformed
        if (!jToggleButton_StartExperiment.isSelected()) { // going to stop acquisition
            stopRun();
            jToggleButton_StartExperiment.setText("Start Experiment");
            jTabbedPane_Input.setEnabled(true);
            phase = ExperimentStage.IDLE;
        } else { // going to start acquisition
            startRun();
            jToggleButton_StartExperiment.setText("Stop Experiment");
            jTabbedPane_Input.setEnabled(false);
        }
    }//GEN-LAST:event_jToggleButton_StartExperimentActionPerformed

    private void jButton_AcceptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_AcceptActionPerformed
        if (myDevice.isSerialConnected()) {
            jPanel_just_Test.setVisible(true);
            jTabbedPane_Input.setEnabled(true);
            jTabbedPane_Monitor.setEnabled(true);
            jButton_Accept.setEnabled(false);
            myDevice.engage();
            jLabel_status.setText("connected   |   engaded");
        }
    }//GEN-LAST:event_jButton_AcceptActionPerformed

    private void jButton_WhoIsThereActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_WhoIsThereActionPerformed
        myDevice.send("BBI;");
    }//GEN-LAST:event_jButton_WhoIsThereActionPerformed

    private void jButton_send_a_messageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_send_a_messageActionPerformed
        myDevice.send(jTextField_command_line.getText());
    }//GEN-LAST:event_jButton_send_a_messageActionPerformed

    private void getMaxTime() {
        par.maxRunningTime = NumString.getDouble(jTextField_MaxTime.getText(), 0.001, 120.0, 15.0);
        jTextField_MaxTime.setText(NumString.format(par.maxRunningTime, 6, 2));
    }

    private void jTextField_MaxTimeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_MaxTimeFocusLost
        getMaxTime();
    }//GEN-LAST:event_jTextField_MaxTimeFocusLost

    private void jRadioButton_ContinuousAcquisitionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_ContinuousAcquisitionActionPerformed
        OverAndOver = jRadioButton_ContinuousAcquisition.isSelected();
    }//GEN-LAST:event_jRadioButton_ContinuousAcquisitionActionPerformed

    private void jRadioButton_wait_NoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_wait_NoneActionPerformed
        par.runningMode = "r";
    }//GEN-LAST:event_jRadioButton_wait_NoneActionPerformed

    private void jRadioButton_wait_for_StartStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_wait_for_StartStopActionPerformed
        par.runningMode = "t";
    }//GEN-LAST:event_jRadioButton_wait_for_StartStopActionPerformed

    private void jRadioButton_wait_for_StartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_wait_for_StartActionPerformed
        par.runningMode = "w";
    }//GEN-LAST:event_jRadioButton_wait_for_StartActionPerformed

    private void jToggleButton_AbortRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton_AbortRunActionPerformed
        stopRun();
    }//GEN-LAST:event_jToggleButton_AbortRunActionPerformed

    private void recalcCurrent() {
        par.capillaryLength = NumString.getDouble(jTextField_CapillaryLength.getText(), 0.1, 999.9, 50.0);
        jTextField_CapillaryLength.setText(NumString.format(par.capillaryLength, 6, 1));
        par.voltage = NumString.getDouble(jTextField_Voltage.getText(), -40.0, 40.0, 30.0);
        jTextField_Voltage.setText(NumString.format(par.voltage, 6, 2));
        par.bgeConductivity = NumString.getDouble(jTextField_BGEconductivity.getText(), 1.0e-07, 20.0, 4.74e-02);
        jTextField_BGEconductivity.setText(String.valueOf(par.bgeConductivity));
        par.capillaryID = NumString.getDouble(jTextField_InnerDiam.getText(), 1.0, 1000.0, 50.0);
        jTextField_InnerDiam.setText(NumString.format(par.capillaryID, 6, 1));
        double current = 0.025 * Math.PI * par.voltage * (par.capillaryID * par.capillaryID) * par.bgeConductivity / par.capillaryLength;
        jLabel_Expected_Current.setText("Expected current: " + NumString.format(current, 6, 1) + " uA");
    }

    private void jTextField_CapillaryLengthFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_CapillaryLengthFocusLost
        recalcCurrent();
    }//GEN-LAST:event_jTextField_CapillaryLengthFocusLost

    private void jTextField_VoltageFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_VoltageFocusLost
        recalcCurrent();
    }//GEN-LAST:event_jTextField_VoltageFocusLost

    private void jTextField_BGEconductivityFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_BGEconductivityFocusLost
        recalcCurrent();
    }//GEN-LAST:event_jTextField_BGEconductivityFocusLost

    private void jTextField_InnerDiamFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_InnerDiamFocusLost
        recalcCurrent();
    }//GEN-LAST:event_jTextField_InnerDiamFocusLost

    private void jTextField_tMfirstFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_tMfirstFocusLost
        par.tMfirst = NumString.getDouble(jTextField_tMfirst.getText(), 0.001, 9999.0, 1.234);
        jTextField_tMfirst.setText(NumString.format(par.tMfirst, 8, 3));
    }//GEN-LAST:event_jTextField_tMfirstFocusLost

    private void jTextField_tMsecondFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_tMsecondFocusLost
        par.tMsecond = NumString.getDouble(jTextField_tMsecond.getText(), 0.001, 9999.0, 1.234);
        jTextField_tMsecond.setText(NumString.format(par.tMsecond, 8, 3));
    }//GEN-LAST:event_jTextField_tMsecondFocusLost

    private void jTextField_tMrefFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField_tMrefFocusLost
        par.tMref = NumString.getDouble(jTextField_tMref.getText(), 0.001, 9999.0, 1.234);
        jTextField_tMref.setText(NumString.format(par.tMref, 8, 3));
    }//GEN-LAST:event_jTextField_tMrefFocusLost

    private void jButton_UpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_UpdateActionPerformed
        updateEach300ms.setEnabled(false);
        fAdjT_1 = par.tMref / par.tMfirst;
        fAdjT_2 = par.tMref / par.tMsecond;
        baseline = (sumBaseline / numBaseline) / 2;
        graphAdj.removeAllPoints();
        for (int i = 0; i < graph1st.xy.getItemCount() - 1; i++) {
            graphAdj.addPoint1(fAdjT_1 * graph1st.xy.getDataItem(i).getXValue(), graph1st.xy.getDataItem(i).getYValue() + baseline + 100, false);
            graphAdj.addPoint2(fAdjT_2 * graph2nd.xy.getDataItem(i).getXValue(), graph2nd.xy.getDataItem(i).getYValue() - baseline, false);
        }
        updateEach300ms.setEnabled(true);
    }//GEN-LAST:event_jButton_UpdateActionPerformed

    private void jTextField_MaxTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_MaxTimeActionPerformed
        getMaxTime();
    }//GEN-LAST:event_jTextField_MaxTimeActionPerformed

    private void jRadioButton_save1stActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_save1stActionPerformed
        par.save1stC4D = jRadioButton_save1st.isSelected();
    }//GEN-LAST:event_jRadioButton_save1stActionPerformed

    private void jRadioButton_save2ndActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton_save2ndActionPerformed
        par.save2ndC4D = jRadioButton_save2nd.isSelected();
    }//GEN-LAST:event_jRadioButton_save2ndActionPerformed

    private void jButton_rescanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_rescanActionPerformed
        jComboBox_port.setModel(new javax.swing.DefaultComboBoxModel(SerialPortList.getPortNames()));
    }//GEN-LAST:event_jButton_rescanActionPerformed

    private void jTextField_CapillaryLengthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_CapillaryLengthActionPerformed
        recalcCurrent();        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField_CapillaryLengthActionPerformed

    private void jTextField_InnerDiamActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_InnerDiamActionPerformed
        recalcCurrent();        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField_InnerDiamActionPerformed

    private void jTextField_VoltageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_VoltageActionPerformed
        recalcCurrent();       // TODO add your handling code here:
    }//GEN-LAST:event_jTextField_VoltageActionPerformed

    private void jTextField_BGEconductivityActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_BGEconductivityActionPerformed
        recalcCurrent();  // TODO add your handling code here:
    }//GEN-LAST:event_jTextField_BGEconductivityActionPerformed

    private void jButton_save_ReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_save_ReportActionPerformed
        SimpleFileWriter arq = new SimpleFileWriter();
        arq.open(myDevice.getFullArqName() + "_report.txt");
        if (arq.isOpen()) {
            if (jCheckBox_saveElectrophoreticConditions.isSelected()) {
                arq.writeln("Electrophoretic conditions");
                arq.writeln("Capillary length: " + NumString.format(par.capillaryLength, 6, 1) + " cm");
                arq.writeln("Capillary i.d.: " + NumString.format(par.capillaryID, 6, 1) + " um");
                arq.writeln("Voltage: " + NumString.format(par.voltage, 6, 2) + " kV");
                arq.writeln("BGE conductivity: " + NumString.format(par.bgeConductivity, 6, 1) + " S/m");
                arq.writeln("");
            }
            arq.writeln("Comments:");
            arq.write(jTextPane_Comments.getText());
            arq.close();
        }
    }//GEN-LAST:event_jButton_save_ReportActionPerformed

    /**
     * This adapter allows a safe ending procedure for the application when the
     * window is closed.
     */
    private class CleanUpAdapter extends WindowAdapter {

        @Override
        public void windowClosing(WindowEvent we) {
            System.out.println("Cleaning and ending the program...");
            par.save("last.par");
            myDevice.close();
            System.exit(0);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(FrontEnd.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(FrontEnd.class.getName()).log(Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> {
            new FrontEnd().setVisible(true);
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel PanelStatus;
    private javax.swing.ButtonGroup buttonGroup_runningModeDA;
    private javax.swing.JButton jButton_Accept;
    private javax.swing.JButton jButton_ConectSerial;
    private javax.swing.JButton jButton_Update;
    private javax.swing.JButton jButton_WhoIsThere;
    private javax.swing.JButton jButton_Zero;
    private javax.swing.JButton jButton_chooseDirectory;
    private javax.swing.JButton jButton_chooseDirectoryBackup;
    private javax.swing.JButton jButton_halt;
    private javax.swing.JButton jButton_once;
    private javax.swing.JButton jButton_rescan;
    private javax.swing.JButton jButton_save_Report;
    private javax.swing.JButton jButton_send_a_message;
    private javax.swing.JCheckBox jCheckBox_saveElectrophoreticConditions;
    private javax.swing.JComboBox jComboBox_baudRate;
    private javax.swing.JComboBox jComboBox_complement;
    private javax.swing.JComboBox jComboBox_port;
    private javax.swing.JComboBox jComboBox_separator;
    private javax.swing.JFileChooser jFileChooser_directory;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel jLabel_Expected_Current;
    private javax.swing.JLabel jLabel_Phase;
    private javax.swing.JLabel jLabel_areFilesClosed;
    private javax.swing.JLabel jLabel_continuousMode;
    private javax.swing.JLabel jLabel_directory;
    private javax.swing.JLabel jLabel_directory1;
    private javax.swing.JLabel jLabel_directoryBackup;
    private javax.swing.JLabel jLabel_from_the_port;
    private javax.swing.JLabel jLabel_status;
    private javax.swing.JLabel jLabel_title_from_the_serial;
    private javax.swing.JLabel jLabel_waitingPulse;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel_Conditions;
    private javax.swing.JPanel jPanel_Connection;
    private javax.swing.JPanel jPanel_Graph;
    private javax.swing.JPanel jPanel_MonitorA;
    private javax.swing.JPanel jPanel_MonitorB;
    private javax.swing.JPanel jPanel_This_Port;
    private javax.swing.JPanel jPanel_just_Test;
    private javax.swing.JPanel jPanel_mold1st;
    private javax.swing.JPanel jPanel_mold2nd;
    private javax.swing.JPanel jPanel_moldAdj;
    private javax.swing.JRadioButton jRadioButton_ContinuousAcquisition;
    private javax.swing.JRadioButton jRadioButton_autoScale_1;
    private javax.swing.JRadioButton jRadioButton_autoScale_2;
    private javax.swing.JRadioButton jRadioButton_autoScale_Adj;
    private javax.swing.JRadioButton jRadioButton_save1st;
    private javax.swing.JRadioButton jRadioButton_save2nd;
    private javax.swing.JRadioButton jRadioButton_wait_None;
    private javax.swing.JRadioButton jRadioButton_wait_for_Start;
    private javax.swing.JRadioButton jRadioButton_wait_for_StartStop;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTabbedPane jTabbedPane_Input;
    private javax.swing.JTabbedPane jTabbedPane_Monitor;
    private javax.swing.JTextField jTextField_BGEconductivity;
    private javax.swing.JTextField jTextField_CapillaryLength;
    private javax.swing.JTextField jTextField_InnerDiam;
    private javax.swing.JTextField jTextField_MaxTime;
    private javax.swing.JTextField jTextField_Prefix;
    private javax.swing.JTextField jTextField_Voltage;
    private javax.swing.JTextField jTextField_command_line;
    private javax.swing.JTextField jTextField_fileName;
    private javax.swing.JTextField jTextField_tMfirst;
    private javax.swing.JTextField jTextField_tMref;
    private javax.swing.JTextField jTextField_tMsecond;
    private javax.swing.JTextPane jTextPane_Comments;
    private javax.swing.JToggleButton jToggleButton_AbortRun;
    private javax.swing.JToggleButton jToggleButton_StartExperiment;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
