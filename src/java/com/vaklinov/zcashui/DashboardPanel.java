/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016 Ivan Vaklinov <ivan@vaklinov.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 **********************************************************************************/
package com.vaklinov.zcashui;


import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;

import com.vaklinov.zcashui.ZCashClientCaller.NetworkAndBlockchainInfo;
import com.vaklinov.zcashui.ZCashClientCaller.WalletBalance;
import com.vaklinov.zcashui.ZCashClientCaller.WalletCallException;
import com.vaklinov.zcashui.ZCashInstallationObserver.DAEMON_STATUS;
import com.vaklinov.zcashui.ZCashInstallationObserver.DaemonInfo;


/**
 * Dashboard ...
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class DashboardPanel
	extends JPanel
{
	private ZCashInstallationObserver installationObserver;
	private ZCashClientCaller clientCaller;
	private StatusUpdateErrorReporter errorReporter;
	
	private JLabel networkAndBlockchainLabel = null;
	private DataGatheringThread<NetworkAndBlockchainInfo> netInfoGatheringThread = null;

	private String OSInfo              = null;
	private JLabel daemonStatusLabel   = null;
	private DataGatheringThread<DaemonInfo> daemonInfoGatheringThread = null;
	
	private JLabel walletBalanceLabel  = null;
	private DataGatheringThread<WalletBalance> walletBalanceGatheringThread = null;
	
	private JTable transactionsTable   = null;
	private JScrollPane transactionsTablePane  = null;
	private String[][] lastTransactionsData = null;
	private DataGatheringThread<String[][]> transactionGatheringThread = null;


	public DashboardPanel(ZCashInstallationObserver installationObserver,
			              ZCashClientCaller clientCaller,
			              StatusUpdateErrorReporter errorReporter)
		throws IOException, InterruptedException, WalletCallException
	{
		this.installationObserver = installationObserver;
		this.clientCaller = clientCaller;
		this.errorReporter = errorReporter;

		// Build content
		JPanel dashboard = this;
		dashboard.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		dashboard.setLayout(new BorderLayout(0, 0));

		// Upper panel with wallet balance
		JPanel balanceStatusPanel = new JPanel();
		// Use border layout to have balances to the left
		balanceStatusPanel.setLayout(new BorderLayout(3, 3)); 
		balanceStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		
		JLabel zcLabel = new JLabel("ZCash Wallet       ");
		zcLabel.setFont(new Font("Helvetica", Font.BOLD | Font.ITALIC, 35));
		balanceStatusPanel.add(zcLabel, BorderLayout.WEST);
				
		JLabel transactionHeadingLabel = new JLabel("<html><br/>Transactions:</html>");
		transactionHeadingLabel.setFont(new Font("Helvetica", Font.BOLD, 20));
		balanceStatusPanel.add(transactionHeadingLabel, BorderLayout.CENTER);
						
		balanceStatusPanel.add(walletBalanceLabel = new JLabel(), BorderLayout.EAST);
		
		dashboard.add(balanceStatusPanel, BorderLayout.NORTH);

		// Table of transactions
		lastTransactionsData = getTransactionsDataFromWallet();
		dashboard.add(transactionsTablePane = new JScrollPane(
				         transactionsTable = this.createTransactionsTable(lastTransactionsData)),
				      BorderLayout.CENTER);

		// Lower panel with installation status
		JPanel installationStatusPanel = new JPanel();
		installationStatusPanel.setLayout(new BorderLayout(3, 3));
		installationStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		installationStatusPanel.add(daemonStatusLabel = new JLabel(), BorderLayout.WEST);
		installationStatusPanel.add(networkAndBlockchainLabel = new JLabel(), BorderLayout.EAST);		
		dashboard.add(installationStatusPanel, BorderLayout.SOUTH);

		// Thread and timer to update the daemon status
		this.daemonInfoGatheringThread = new DataGatheringThread<DaemonInfo>(
			new DataGatheringThread.DataGatherer<DaemonInfo>() 
			{
				public DaemonInfo gatherData()
					throws Exception
				{
					long start = System.currentTimeMillis();
					DaemonInfo daemonInfo = DashboardPanel.this.installationObserver.getDaemonInfo();
					long end = System.currentTimeMillis();
					System.out.println("Gathering of dashboard daemon status data done in " + (end - start) + "ms." );
					
					return daemonInfo;
				}
			}, 
			this.errorReporter, 2000, true);
		
		ActionListener alDeamonStatus = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					DashboardPanel.this.updateDaemonStatusLabel();
				} catch (Exception ex)
				{
					ex.printStackTrace();
					DashboardPanel.this.errorReporter.reportError(ex);
				}
			}
		};
		new Timer(1000, alDeamonStatus).start();
		
		// Thread and timer to update the wallet balance
		this.walletBalanceGatheringThread = new DataGatheringThread<WalletBalance>(
			new DataGatheringThread.DataGatherer<WalletBalance>() 
			{
				public WalletBalance gatherData()
					throws Exception
				{
					long start = System.currentTimeMillis();
					WalletBalance balance = DashboardPanel.this.clientCaller.getWalletInfo();
					long end = System.currentTimeMillis();
					System.out.println("Gathering of dashboard wallet balance data done in " + (end - start) + "ms." );
					
					return balance;
				}
			}, 
			this.errorReporter, 8000, true);
		
		ActionListener alWalletBalance = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					DashboardPanel.this.updateWalletStatusLabel();
				} catch (Exception ex)
				{
					ex.printStackTrace();
					DashboardPanel.this.errorReporter.reportError(ex);
				}
			}
		};
		Timer walletBalanceTimer =  new Timer(2000, alWalletBalance);
		walletBalanceTimer.setInitialDelay(1000);
		walletBalanceTimer.start();

		// Thread and timer to update the transactions table
		this.transactionGatheringThread = new DataGatheringThread<String[][]>(
			new DataGatheringThread.DataGatherer<String[][]>() 
			{
				public String[][] gatherData()
					throws Exception
				{
					long start = System.currentTimeMillis();
					String[][] data =  DashboardPanel.this.getTransactionsDataFromWallet();
					long end = System.currentTimeMillis();
					System.out.println("Gathering of dashboard wallet transactions table data done in " + (end - start) + "ms." );
					
					return data;
				}
			}, 
			this.errorReporter, 25000);
		
		ActionListener alTransactions = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try
				{					
					DashboardPanel.this.updateWalletTransactionsTable();
				} catch (Exception ex)
				{
					ex.printStackTrace();
					DashboardPanel.this.errorReporter.reportError(ex);
				}
			}
		};
		new Timer(5000, alTransactions).start();

		// Thread and timer to update the network and blockchain details
		this.netInfoGatheringThread = new DataGatheringThread<NetworkAndBlockchainInfo>(
			new DataGatheringThread.DataGatherer<NetworkAndBlockchainInfo>() 
			{
				public NetworkAndBlockchainInfo gatherData()
					throws Exception
				{
					long start = System.currentTimeMillis();
					NetworkAndBlockchainInfo data =  DashboardPanel.this.clientCaller.getNetworkAndBlockchainInfo();
					long end = System.currentTimeMillis();
					System.out.println("Gathering of network and blockchain info data done in " + (end - start) + "ms." );
					
					return data;
				}
			}, 
			this.errorReporter, 10000, true);
		
		ActionListener alNetAndBlockchain = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					DashboardPanel.this.updateNetworkAndBlockchainLabel();
				} catch (Exception ex)
				{
					ex.printStackTrace();
					DashboardPanel.this.errorReporter.reportError(ex);
				}
			}
		};
		Timer netAndBlockchainTimer = new Timer(5000, alNetAndBlockchain);
		netAndBlockchainTimer.setInitialDelay(1000);
		netAndBlockchainTimer.start();
	}


	private void updateDaemonStatusLabel()
		throws IOException, InterruptedException
	{
		DaemonInfo daemonInfo = this.daemonInfoGatheringThread.getLastData();
		
		// It is possible there has been no gathering initially
		if (daemonInfo == null)
		{
			return;
		}
		
		String daemonStatus = "<span style=\"color:green;font-weight:bold\">RUNNING</span>";
		if (daemonInfo.status != DAEMON_STATUS.RUNNING)
		{
			daemonStatus = "<span style=\"color:red;font-weight:bold\">NOT RUNNING</span>";
		}
		String runtimeInfo = "";
		if (daemonInfo.status == DAEMON_STATUS.RUNNING)
		{
			runtimeInfo = "Resident: " + daemonInfo.residentSizeMB + " MB, Virtual: " + daemonInfo.virtualSizeMB +
					      " MB, CPU: " + daemonInfo.cpuPercentage + "%";
		}

		File walletDAT = new File(OSUtil.getBlockchainDirectory() + "/wallet.dat");
		
		if (this.OSInfo == null)
		{
			this.OSInfo = OSUtil.getSystemInfo();
		}
		
		String text =
			"<html><span style=\"font-weight:bold\">zcashd</span> status: " + 
		    daemonStatus + ",  " + runtimeInfo + " <br/>" +
			"Installation directory: " + OSUtil.getProgramDirectory() + " <br/> " +
	        "Blockchain directory: " + OSUtil.getBlockchainDirectory() + ", " +
			"Wallet file: " + walletDAT.getCanonicalPath() + " <br/> " +
		    "System: " + this.OSInfo + "</html>";
		this.daemonStatusLabel.setText(text);
	}

	
	private void updateNetworkAndBlockchainLabel()
		throws IOException, InterruptedException
	{
		NetworkAndBlockchainInfo info = this.netInfoGatheringThread.getLastData();
			
		// It is possible there has been no gathering initially
		if (info == null)
		{
			return;
		}
				
		String text =
			"<html> <br/> <br/> <br/> " +
			"Network: <span style=\"font-weight:bold\">" + info.numConnections + " connections </span>";
		this.networkAndBlockchainLabel.setText(text);
	}
	

	private void updateWalletStatusLabel()
		throws WalletCallException, IOException, InterruptedException
	{
		WalletBalance balance = this.walletBalanceGatheringThread.getLastData();
		
		// It is possible there has been no gathering initially
		if (balance == null)
		{
			return;
		}

		String text =
			"<html><span style=\"\">Transparent balance: " + balance.transparentBalance + " ZEC </span><br/> " +
			"Private ( Z ) balance: <span style=\"font-weight:bold\">" + balance.privateBalance + " ZEC </span><br/> " +
			"Total ( Z+T ) balance: <span style=\"font-weight:bold\">" + balance.totalBalance + " ZEC </span> <br/>  </html>";
		this.walletBalanceLabel.setText(text);
	}


	private void updateWalletTransactionsTable()
		throws WalletCallException, IOException, InterruptedException
	{
		String[][] newTransactionsData = this.transactionGatheringThread.getLastData();
		
		// May be null - not even gathered once
		if (newTransactionsData == null)
		{
			return;
		}

		if (lastTransactionsData.length != newTransactionsData.length)
		{
			this.remove(transactionsTablePane);
			this.add(transactionsTablePane = new JScrollPane(
			             transactionsTable = this.createTransactionsTable(newTransactionsData)),
			         BorderLayout.CENTER);
		}

		lastTransactionsData = newTransactionsData;

		this.validate();
		this.repaint();
	}


	private JTable createTransactionsTable(String rowData[][])
		throws WalletCallException, IOException, InterruptedException
	{
		String columnNames[] = { "Type", "Direction", "Amount", "Date", "Address"};
        JTable table = new JTable(rowData, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(390);
        table.getColumnModel().getColumn(4).setPreferredWidth(800);

        return table;
	}


	private String[][] getTransactionsDataFromWallet()
		throws WalletCallException, IOException, InterruptedException
	{
		// Get available public+private transactions and unify them.
		String[][] publicTransactions = this.clientCaller.getWalletPublicTransactions();
		String[][] zReceivedTransactions = this.clientCaller.getWalletZReceivedTransactions();

		String[][] allTransactions = new String[publicTransactions.length + zReceivedTransactions.length][];

		int i  = 0;

		for (String[] t : publicTransactions)
		{
			allTransactions[i++] = t;
		}

		for (String[] t : zReceivedTransactions)
		{
			allTransactions[i++] = t;
		}
		
		// Sort transactions by date
		Arrays.sort(allTransactions, new Comparator<String[]>() {
			public int compare(String[] o1, String[] o2)
			{
				Date d1 = new Date(0);
				if (!o1[3].equals("N/A"))
				{
					d1 = new Date(Long.valueOf(o1[3]).longValue() * 1000L);
				}

				Date d2 = new Date(0);
				if (!o2[3].equals("N/A"))
				{
					d2 = new Date(Long.valueOf(o2[3]).longValue() * 1000L);
				}

				if (d1.equals(d2))
				{
					return 0;
				} else
				{
					return d2.compareTo(d1);
				}
			}
		});

		// Change the direction and date attributes for presentation purposes
		for (String[] t : allTransactions)
		{
			if (t[1].equals("receive"))
			{
				t[1] = "\u21E8 IN";
			} else if (t[1].equals("send"))
			{
				t[1] = "\u21E6 OUT";
			};

			if (!t[3].equals("N/A"))
			{
				t[3] = new Date(Long.valueOf(t[3]).longValue() * 1000L).toLocaleString();
			}
		}


		return allTransactions;
	}
}
