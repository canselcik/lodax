package com.originblue.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class LDConfigurationUI extends JDialog {

	private final JPanel contentPanel = new JPanel();
	private Properties props;
	private JTextField txtSecret;
	private JTextField txtKey;
	private JTextField txtPassphrase;
	private JButton btnOk;
	private final LDConfigurationUI dialog;
	private static final String CONFPATH = System.getProperty("user.home") + File.separatorChar + "lodax.properties";

	// Will block until modal is disposed of
	public Properties prompt() {
		this.setModal(true);
		this.setVisible(true);
		if (props != null)
			return props;
		return getProperties();
	}

	public static Properties getProperties() {
		try {
			File file = new File(CONFPATH);
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();
			return properties;
		} catch (Exception e) { }
		return new Properties();
	}

	public static boolean writeProperties(Properties p) {
		try {
			p.store(new FileOutputStream(CONFPATH), null);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public LDConfigurationUI() {
		dialog = this;
		setTitle("Configuration");
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(null);
		{
			final JLabel lblGdaxSecret = new JLabel("GDAX Secret:");
			lblGdaxSecret.setBounds(24, 24, 100, 14);
			contentPanel.add(lblGdaxSecret);
		}
		
		final JLabel lblGdaxKeyyy = new JLabel("GDAX Key:");
		lblGdaxKeyyy.setBounds(24, 49, 100, 14);
		contentPanel.add(lblGdaxKeyyy);
		
		final JLabel lblGdaxPassphrase = new JLabel("GDAX Passphrase:");
		lblGdaxPassphrase.setBounds(24, 74, 128, 14);
		contentPanel.add(lblGdaxPassphrase);
		
		this.txtSecret = new JTextField();
		this.txtSecret.setBounds(150, 21, 274, 20);
		contentPanel.add(this.txtSecret);
		this.txtSecret.setColumns(10);
		
		this.txtKey = new JTextField();
		this.txtKey.setColumns(10);
		this.txtKey.setBounds(150, 46, 274, 20);
		contentPanel.add(this.txtKey);
		
		this.txtPassphrase = new JTextField();
		this.txtPassphrase.setColumns(10);
		this.txtPassphrase.setBounds(150, 71, 274, 20);
		contentPanel.add(this.txtPassphrase);
		
		final JCheckBox chckbxSave = new JCheckBox("Save");
		chckbxSave.setHorizontalAlignment(SwingConstants.TRAILING);
		chckbxSave.setBounds(271, 98, 153, 23);
		chckbxSave.setSelected(true);
		contentPanel.add(chckbxSave);
		
		final JLabel lblThisInformationIs = new JLabel("This information is stored unencrypted in your $HOMEDIR.");
		lblThisInformationIs.setBounds(24, 131, 400, 35);
		contentPanel.add(lblThisInformationIs);
		
		final JLabel lblToThisApplication = new JLabel("You'll be prompted every time unless saved.");
		lblToThisApplication.setBounds(24, 149, 386, 35);
		contentPanel.add(lblToThisApplication);
		
		final JLabel lblMonitoringWithoutEntering = new JLabel("Monitoring without entering keys is supported.");
		lblMonitoringWithoutEntering.setBounds(24, 177, 318, 14);
		contentPanel.add(lblMonitoringWithoutEntering);
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				btnOk = new JButton("OK");
				this.btnOk.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String pass = txtPassphrase.getText();
						String key = txtKey.getText();
						String secret = txtSecret.getText();
						Properties prop = getProperties();
						prop.setProperty("GDAX_KEY", key);
						prop.setProperty("GDAX_PASSPHRASE", pass);
						prop.setProperty("GDAX_SECRET", secret);
						if (chckbxSave.isSelected()) {
							writeProperties(prop);
						}
						dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
						dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
					}
				});
				btnOk.setActionCommand("OK");
				buttonPane.add(btnOk);
				getRootPane().setDefaultButton(btnOk);
			}
		}

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		props = getProperties();
		if (props.containsKey("GDAX_KEY"))
			txtKey.setText(props.getProperty("GDAX_KEY"));
		if (props.containsKey("GDAX_PASSPHRASE"))
			txtPassphrase.setText(props.getProperty("GDAX_PASSPHRASE"));
		if (props.containsKey("GDAX_SECRET"))
			txtSecret.setText(props.getProperty("GDAX_SECRET"));
	}
}
