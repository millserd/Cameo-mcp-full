package com.cameomcp.plugin.ui;

import com.cameomcp.plugin.CameoLog;
import com.cameomcp.plugin.CameoMcpPlugin;
import com.cameomcp.plugin.McpRuntime;
import com.cameomcp.plugin.Settings;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * In-App Swing Status & Diagnostics Window (com.cameomcp.plugin.ui.McpStatusWindow).
 *
 * Displays:
 * - Server state, active endpoint (http://127.0.0.1:18740/mcp), and port configuration
 * - Connected MCP client sessions, total requests, and 365-tool catalog summary
 * - Bearer token configuration and macro execution safety toggle
 * - One-click Connection Self-Test (publishing directly to Cameo's Notification window & md.log)
 * - Force Reset Stuck SessionManager Transaction & Copy Client Config JSON
 */
public class McpStatusWindow extends JDialog {

    private static McpStatusWindow activeWindow;
    private final CameoMcpPlugin plugin;
    private final JLabel statusLabel = new JLabel();
    private final JLabel clientsLabel = new JLabel();
    private final JLabel projectLabel = new JLabel();
    private final JTextField portField = new JTextField(7);
    private final JTextField tokenField = new JTextField(16);
    private final JCheckBox allowMacrosCheck = new JCheckBox("Allow Groovy/Jython Macro Execution");
    private final JTextArea logArea = new JTextArea(14, 72);
    private final Timer refreshTimer;

    public McpStatusWindow(Frame owner, CameoMcpPlugin plugin) {
        super(owner, "Cameo MCP — Server Status & Diagnostics (2024x / 2026x)", false);
        this.plugin = plugin;
        Settings s = plugin.getSettings();
        this.portField.setText(String.valueOf(s.getPort()));
        this.tokenField.setText(s.getBearerToken());
        this.allowMacrosCheck.setSelected(s.isAllowMacros());
        buildUi();
        refreshState();
        this.refreshTimer = new Timer(1500, e -> refreshState());
        this.refreshTimer.start();
    }

    public static void showWindow(CameoMcpPlugin plugin) {
        CameoLog.info("Opening MCP Server Status window...");
        SwingUtilities.invokeLater(() -> {
            try {
                Frame mainFrame = Application.getInstance().getMainFrame();
                if (activeWindow == null || !activeWindow.isDisplayable()) {
                    activeWindow = new McpStatusWindow(mainFrame, plugin);
                    activeWindow.pack();
                    activeWindow.setLocationRelativeTo(mainFrame);
                }
                activeWindow.refreshState();
                activeWindow.setVisible(true);
                activeWindow.toFront();
                CameoLog.info("MCP Server Status window shown at " + activeWindow.getBounds());
            } catch (Throwable t) {
                // Exceptions inside invokeLater are otherwise swallowed silently by Cameo's EDT
                CameoLog.error("Failed to open MCP Server Status window", t);
                JTextArea trace = new JTextArea(CameoLog.getLastErrorTrace(), 20, 90);
                trace.setEditable(false);
                JOptionPane.showMessageDialog(null, new JScrollPane(trace),
                        "Cameo MCP — Status window failed to open", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 8, 6));
        infoGrid.setBorder(new TitledBorder("Cameo MCP Runtime & Security Controls (com.cameomcp.plugin)"));

        infoGrid.add(new JLabel("MCP Endpoint Status:"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        infoGrid.add(statusLabel);

        infoGrid.add(new JLabel("Connected Clients / Requests / Tools:"));
        infoGrid.add(clientsLabel);

        infoGrid.add(new JLabel("Active Project & SessionManager:"));
        infoGrid.add(projectLabel);

        infoGrid.add(new JLabel("Port & Bearer Token (Optional):"));
        JPanel configRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        configRow.add(new JLabel("Port:"));
        configRow.add(portField);
        configRow.add(new JLabel("Token:"));
        configRow.add(tokenField);
        JButton applyBtn = new JButton("Save & Restart");
        applyBtn.addActionListener(e -> {
            try {
                int newPort = Integer.parseInt(portField.getText().trim());
                plugin.getSettings().setBearerToken(tokenField.getText().trim());
                plugin.getSettings().setAllowMacros(allowMacrosCheck.isSelected());
                plugin.getRuntime().getCallGuard().setBearerToken(tokenField.getText().trim());
                plugin.getRuntime().getCallGuard().setAllowMacros(allowMacrosCheck.isSelected());
                plugin.getRuntime().restartWithPort(newPort);
                refreshState();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        configRow.add(applyBtn);
        infoGrid.add(configRow);

        infoGrid.add(new JLabel("Macro Execution Gate:"));
        allowMacrosCheck.addActionListener(e -> {
            plugin.getSettings().setAllowMacros(allowMacrosCheck.isSelected());
            plugin.getRuntime().getCallGuard().setAllowMacros(allowMacrosCheck.isSelected());
            plugin.getSettings().save();
        });
        infoGrid.add(allowMacrosCheck);

        root.add(infoGrid, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new TitledBorder("Live MCP Call & Diagnostic Log (Mirrored to Cameo Notification Window & md.log)"));
        root.add(scroll, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton selfTestBtn = new JButton("Run Connection Self-Test");
        selfTestBtn.addActionListener(e -> {
            String res = ConnectionTest.runSelfTest(plugin.getSettings().getHost(), plugin.getSettings().getPort());
            CameoLog.info(res);
            refreshState();
        });

        JButton resetSessionBtn = new JButton("Reset Stuck Editing Session");
        resetSessionBtn.addActionListener(e -> {
            Project proj = Application.getInstance().getProject();
            if (proj != null && SessionManager.getInstance().isSessionCreated(proj)) {
                SessionManager.getInstance().cancelSession(proj);
                CameoLog.warn("Cancelled active SessionManager transaction on project " + proj.getName());
            } else {
                CameoLog.info("SessionManager check: no stuck session active.");
            }
            refreshState();
        });

        JButton copyCfgBtn = new JButton("Copy Client Config JSON");
        copyCfgBtn.addActionListener(e -> {
            ClientConfigs.copyToClipboard(plugin.getSettings().getHost(), plugin.getSettings().getPort());
            CameoLog.info("Copied MCP client configuration JSON to clipboard.");
            refreshState();
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> setVisible(false));

        btnBar.add(selfTestBtn);
        btnBar.add(resetSessionBtn);
        btnBar.add(copyCfgBtn);
        btnBar.add(closeBtn);

        root.add(btnBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    public void refreshState() {
        try {
            doRefreshState();
        } catch (Throwable t) {
            CameoLog.error("Status window refresh failed", t);
            if (refreshTimer != null) refreshTimer.stop(); // don't repeat the same failure every 1.5s
            statusLabel.setText("REFRESH ERROR — " + t);
            statusLabel.setForeground(new Color(180, 20, 20));
        }
    }

    private void doRefreshState() {
        McpRuntime rt = plugin.getRuntime();
        Settings s = plugin.getSettings();
        if (rt != null && rt.isRunning()) {
            statusLabel.setText(String.format("ONLINE — http://%s:%d/mcp (%s)",
                    s.getHost(), s.getPort(), McpRuntime.detectCameoRelease()));
            statusLabel.setForeground(new Color(0, 128, 64));
        } else {
            statusLabel.setText("OFFLINE — Port " + s.getPort());
            statusLabel.setForeground(new Color(180, 20, 20));
        }

        clientsLabel.setText(String.format("%d active session(s) | %d requests handled | 365 tools across 7 families",
                rt != null ? rt.getActiveClientSessionCount() : 0,
                rt != null ? rt.getTotalRequests() : 0));

        Project proj = Application.getInstance().getProject();
        if (proj != null) {
            boolean inSess = SessionManager.getInstance().isSessionCreated(proj);
            projectLabel.setText(proj.getName() + (proj.isDirty() ? " (dirty)" : " (saved)") +
                    " | Session: " + (inSess ? "ACTIVE" : "Idle"));
        } else {
            projectLabel.setText("No project open");
        }

        StringBuilder sb = new StringBuilder();
        for (String line : CameoLog.getRecentLogs()) {
            sb.append(line).append('\n');
        }
        logArea.setText(sb.toString());
    }
}
