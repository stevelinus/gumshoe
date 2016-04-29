package com.dell.gumshoe.inspector;

import static com.dell.gumshoe.util.Swing.flow;
import static com.dell.gumshoe.util.Swing.groupButtons;
import static com.dell.gumshoe.util.Swing.rows;

import com.dell.gumshoe.ProbeManager;
import com.dell.gumshoe.inspector.helper.DataTypeHelper;
import com.dell.gumshoe.stack.Stack;
import com.dell.gumshoe.stats.StatisticAdder;
import com.dell.gumshoe.stats.ValueReporter.Listener;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ProbeSourcePanel extends JPanel implements Listener<StatisticAdder> {
    private static final SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss");

    private final StatisticsSourcePanel parent;

    private final JRadioButton ignoreIncoming = new JRadioButton("ignore new samples");
    private final JRadioButton dropOldest = new JRadioButton("drop oldest sample", true);
    private final JCheckBox sendLive = new JCheckBox("Immediately show newest");
    private final JButton sendNow = new JButton("Display selected");
    private int sampleCount = 3;
    private final DefaultListModel sampleModel = new DefaultListModel();
    private final JList sampleList = new JList(sampleModel);

    public ProbeSourcePanel(StatisticsSourcePanel parent, ProbeManager probe) {
        this.parent = parent;

        sendNow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Sample selected = (Sample)sampleList.getSelectedValue();
                if(selected!=null) {
                    relayStats(selected);
                }
            }
        });

        sampleList.setVisibleRowCount(sampleCount);
        sampleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if( ! sendNow.isSelected() && e.getClickCount()==2) {
                    int index = sampleList.locationToIndex(e.getPoint());
                    final Sample selected = (Sample)sampleModel.getElementAt(index);
                    if(selected!=null) {
                        relayStats(selected);
                    }
                 }
            }
        });

        final JPanel acceptPanel = new JPanel();
        acceptPanel.add(new JLabel("Accept:"));
        for(String type : DataTypeHelper.getTypes()) {
            acceptPanel.add(DataTypeHelper.forType(type).getSelectionComponent());
        }

        groupButtons(ignoreIncoming, dropOldest);

        final JLabel fullLabel = new JLabel("When buffer full:");
        final JPanel handleIncoming = flow(sendLive,  fullLabel, ignoreIncoming, dropOldest);
        sendLive.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final boolean manualMode = ! sendLive.isSelected();
                sendNow.setEnabled(manualMode);
                fullLabel.setEnabled(manualMode);
                ignoreIncoming.setEnabled(manualMode);
                dropOldest.setEnabled(manualMode);
            }
        });

        final JPanel optionPanel = rows(acceptPanel, handleIncoming);

        setLayout(new BorderLayout());
        add(optionPanel, BorderLayout.NORTH);
        add(new JScrollPane(sampleList, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        add(flow(sendNow), BorderLayout.SOUTH);

        if(probe!=null) {
            for(String helperType : DataTypeHelper.getTypes()) {
                DataTypeHelper.forType(helperType).addListener(probe, this);
            }
        }
    }

    private boolean canAccept(String sampleType, Map<Stack,StatisticAdder> stats) {
        if(stats.isEmpty()) { return false; }

        // see if the box is checked to accept this sample type
        for(String helperType : DataTypeHelper.getTypes()) {
            if(helperType.equals(sampleType)) {
                if(DataTypeHelper.forType(helperType).isSelected()) {
                    break;
                } else {
                    return false;
                }
            }
        }

        // see if buffer has room or we can remove one
        if(sampleModel.size()>=sampleCount) {
            if(ignoreIncoming.isSelected() && ! sendLive.isSelected()) { return false; }
            sampleModel.removeElementAt(0);
        }
        return true;
    }

    @Override
    public synchronized void statsReported(String type, Map<Stack,StatisticAdder> stats) {
        if(canAccept(type, stats)) {
            final Date date = new Date();
            final Sample sample = new Sample(type, date, stats);
            if(sendLive.isSelected()) {
                sampleModel.clear();
            }
            sampleModel.addElement(sample);
            if(sendLive.isSelected()) {
                relayStats(sample);
            }
        }
    }

    private void relayStats(Sample sample) {
        parent.setStatus("Displaying sample: " + sample.time + " " + sample.label);
        parent.setSample(sample.time, sample.data);
    }

    private static class Sample {
        String label;
        String time;
        String type;
        Map<Stack,StatisticAdder> data;

        public Sample(String type, Date sampleTime, Map<Stack,StatisticAdder> data) {
            this.time =  hms.format(sampleTime);
            this.label = time + " " + type + ": " + DataTypeHelper.forType(type).getSummary(data);
            this.type = type;
            this.data = data;
        }

        @Override
        public String toString() {
            return label;
        }
    }

}