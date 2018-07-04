/*******************************************************************************
 * Copyright (C) 2017 Create-Net / FBK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Create-Net / FBK - initial API and implementation
 ******************************************************************************/

package org.eclipse.agail.protocol.ble;

import java.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.agail.Protocol;
import org.eclipse.agail.ProtocolManager;
import org.eclipse.agail.object.AbstractAgileObject;
import org.eclipse.agail.object.DeviceOverview;
import org.eclipse.agail.object.DeviceStatusType;
import org.eclipse.agail.object.StatusType;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothNotification;
import tinyb.BluetoothType;

/**
 * Agile Bluetooth Low Energy(BLE) Protocol implementation
 *
 * @author dagi
 *
 */
public class BLEProtocolImp extends AbstractAgileObject implements Protocol {

	protected final Logger logger = LoggerFactory.getLogger(BLEProtocolImp.class);

	/**
	 * Bus name for AGILE BLE Protocol
	 */
	private static final String AGILE_BLUETOOTH_BUS_NAME = "org.eclipse.agail.protocol.BLE";

	/**
	 * Bus path for AGILE BLE Protocol
	 */
	private static final String AGILE_BLUETOOTH_BUS_PATH = "/org/eclipse/agail/protocol/BLE";

	/**
	 * DBus bus path for found new device signal
	 */
	private static final String AGILE_NEW_DEVICE_SIGNAL_PATH = "/org/eclipse/agail/NewDevice";

	/**
	 * DBus bus path for found new device signal
	 */
	private static final String AGILE_EXISTING_DEVICE_SIGNAL_PATH = "/org/eclipse/agail/ExistingDevice";

	/**
	 * DBus bus path for for new record/data reading
	 */
	private static final String AGILE_NEW_RECORD_SIGNAL_PATH = "/org/eclipse/agail/NewRecord";

	/**
	 * Protocol name
	 */
	private static final String PROTOCOL_NAME = "Bluetooth Low Energy";

	/**
	 * Protocol driver name
	 */
	private static final String DRIVER_NAME = "BLE";

	// Device status
	public static final String CONNECTED = "CONNECTED";

	public static final String DISCONNECTED = "DISCONNECTED";

	public static final String AVAILABLE = "AVAILABLE";

	public static final String UNAVAILABLE = "AVAILABLE";

	private static final String GATT_SERVICE = "GATT_SERVICE";

	private static final String GATT_CHARACTERSTICS = "GATT_CHARACTERSTICS";

	/**
	 * Device list
	 */
	protected List<DeviceOverview> deviceList = new ArrayList<DeviceOverview>();

	protected List<BluetoothDevice> bleDevices = new ArrayList<BluetoothDevice>();

	/**
	 * The bluetooth manager
	 */
	protected BluetoothManager bleManager;

	protected byte[] lastRecord;

	private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

	private ScheduledFuture future;

	protected final State state = new State();

	public class State {
		public boolean isDiscovering = false;
	}

	public static void main(String[] args) throws DBusException {
		Protocol bleProtocol = new BLEProtocolImp();
	}

	public BLEProtocolImp() throws DBusException {
		dbusConnect(AGILE_BLUETOOTH_BUS_NAME, AGILE_BLUETOOTH_BUS_PATH, this);
		try {
			bleManager = BluetoothManager.getBluetoothManager();
		} catch (BluetoothException bex) {
			logger.error(" Failed to start BLE Protocol, no bluetooth adapter found on the system", bex);
		} catch (Exception e) {
			logger.error("Error in getting BluetoothManager instance", e);
		}
		logger.debug("Started BLE Protocol");

		// fill initial list of devices
//		List<BluetoothDevice> list = bleManager.getDevices();
//		for (BluetoothDevice device : list) {
		List<BluetoothDevice> list = bleManager.getDevices();
		bleDevices = bleManager.getDevices();
		for (BluetoothDevice device : bleDevices) {
			logger.info("{}({}) Conn:{} RSSI:{}", device.getName(), device.getAddress(), device.getConnected(), device.getRSSI());
			if (device.getConnected()) {
				DeviceOverview deviceOverview = new DeviceOverview(device.getAddress(), AGILE_BLUETOOTH_BUS_NAME, device.getName(), CONNECTED, String.valueOf(System.currentTimeMillis()));
				if (isNewDevice(deviceOverview)) {
					deviceList.add(deviceOverview);
					try {
						ProtocolManager.FoundNewDeviceSignal foundNewDevSig = new ProtocolManager.FoundNewDeviceSignal(AGILE_NEW_DEVICE_SIGNAL_PATH, deviceOverview);
						connection.sendSignal(foundNewDevSig);
					} catch (DBusException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 *
	 *
	 * @see org.eclipse.agail.protocol.ble.Protocol#name()
	 */
	@Override
	public String Name() {
		return PROTOCOL_NAME;
	}

	/**
	 *
	 * @see org.eclipse.agail.protocol.ble.Protocol#driver()
	 */
	@Override
	public String Driver() {
		return DRIVER_NAME;
	}

	/**
	 *
	 *
	 * @see org.eclipse.agail.protocol.ble.Protocol#Status()
	 */
	@Override
	public String Status() {
		logger.debug("Protocol.Status not implemented");
		return null;
	}

	/**
	 * Returns lists of devices
	 */
	@Override
	public List<DeviceOverview> Devices() {
		return deviceList;
	}

	/**
	 * @see org.eclipse.agail.protocol.ble.Protocol#DataStore()
	 */
	@Override
	public byte[] Data() {
		return lastRecord;
	}

	/**
	 * Connect BLE Device
	 *
	 * @param deviceAddress
	 * @throws DBusException
	 * @see org.eclipse.agail.protocol.ble.Protocol#initialize(java.lang.String)
	 */
	@Override
	public void Connect(String deviceAddress) throws DBusException {
		logger.info("Connecting to BLE device {}", deviceAddress);
		try {
			BluetoothDevice bleDevice = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress,
					null);
			if (bleDevice != null) {
				if (!bleDevice.getConnected()) {
					bleDevice.connect();
					logger.info("Connected BLE device {}", deviceAddress);
				} else {
					logger.info("BLE device already connected {}", deviceAddress);
				}
			} else {
				logger.warn("Cannot find BLE device {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to connect: {}", deviceAddress, e);
			throw new DBusException("Failed to connect device:" + deviceAddress);
		}
	}

	/**
	 *
	 * Disconnect bluetooth device
	 *
	 * @return
	 * @throws DBusException
	 * @see org.eclipse.agail.protocol.ble.Protocol#destory(java.lang.String)
	 */
	@Override
	public void Disconnect(String deviceAddress) throws DBusException {
		logger.info("Disconnecting from BLE device {}", deviceAddress);
		try {
			BluetoothDevice bleDevice = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress,
					null);
			if (bleDevice != null) {
				if (bleDevice.getConnected()) {
					bleDevice.disconnect();
				}
			}
		} catch (Exception e) {
			logger.error("Failed to disconnect {}", deviceAddress, e);
			throw new DBusException("Failed to disconnect device:" + deviceAddress);
		}
	}

	/**
	 * Discover status
	 *
	 * @see org.eclipse.agail.protocol.ble.Protocol#StopDiscovery()
	 */
	@Override
	public String DiscoveryStatus() {
		if(future != null){
			if(future.isCancelled()){
				return "NONE";
			}else{
				return "RUNNING";
			}	
		}
		return "NONE";
	}

	/**
	 * Discover BLE devices, towards a more descriptive name
	 */
	@Override
	public void StartDiscovery() {
		if (future != null) {
			logger.info("Discovery already running");
			return;
		}

		logger.info("Started discovery of BLE devices");

		bleManager.startDiscovery();

		/* TODO: would be better reactive: lister to bluez/TinyB signals */
		Runnable task = () -> {

			logger.debug("Checking for new devices");

			int newDevices = 0;
			List<BluetoothDevice> list = bleManager.getDevices();
			for (BluetoothDevice device : list) {
				if (device.getRSSI() != 0 || device.getConnected()) {
					DeviceOverview deviceOverview = new DeviceOverview(device.getAddress(), AGILE_BLUETOOTH_BUS_NAME,
							device.getName(), AVAILABLE , String.valueOf(System.currentTimeMillis()));
					if (isNewDevice(deviceOverview)) {
						logger.info("{}({}) Conn:{} RSSI:{}", device.getName(), device.getAddress(), device.getConnected(), device.getRSSI());
                        NewNotificationRssi rssiCallback = new NewNotificationRssi(device);
                        device.enableRSSINotifications(rssiCallback);

 						deviceList.add(deviceOverview);
						try {
							ProtocolManager.FoundNewDeviceSignal foundNewDevSig = new ProtocolManager.FoundNewDeviceSignal(
									AGILE_NEW_DEVICE_SIGNAL_PATH, deviceOverview);
							connection.sendSignal(foundNewDevSig);
						} catch (DBusException e) {
							e.printStackTrace();
						}
						printDevice(device);
						newDevices++;
					}
				}
			}

			if (newDevices > 0) {
				logger.info("Found {} new device(s)", newDevices);
			}
		};

		future = executor.scheduleWithFixedDelay(task, 0, 1, TimeUnit.SECONDS);
	}

	/**
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.agail.protocol.ble.Protocol#StopDiscovery()
	 */
	@Override
	public void StopDiscovery() {
		if (future == null) {
			logger.info("Discovery not running");
			return;
		}

		bleManager.stopDiscovery();
		if (future != null) {
			future.cancel(true);
			future = null;
		}
	}

	/**
	 *
	 *
	 * @throws DBusException
	 * @see org.eclipse.agail.protocol.ble.Protocol#write()
	 */
	@Override
	public void Write(String deviceAddress, Map<String, String> profile, byte[] payload) throws DBusException {
			BluetoothDevice device;
			try {
				device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
				if (device != null) {
					if (device.getConnected()) {
						BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
						if (gattService != null) {
							BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
							if (gattChar != null) {
								synchronized (gattChar) {
								gattChar.writeValue(payload);
							}
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("Device not connected: {}", deviceAddress);
					}
				} else {
					logger.error("Device not found: {}", deviceAddress);
				}
				}
			} catch (Exception e) {
				logger.error("Failed to write value: {}", deviceAddress, e);
					throw new DBusException("Failed to write value:" + deviceAddress);
			}
			}
	

	/**
	 *
	 *
	 * @param profile
	 * @see org.eclipse.agail.protocol.ble.Protocol#read()
	 */
	public byte[] Read(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						if (gattChar != null) {
						synchronized (gattChar) {
							lastRecord = gattChar.readValue();
						}
						return lastRecord;
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to read ", e);
			throw new DBusException("Failed to read value");
		}
		return null;
	}

	/**
	 * Workaround for a TinyB issue: when TinyB is using BluetoothGattService.find() to get a copy of BluetoothGattCharacteristics,
	 * it is returning a new object with a new notification callback attibute inside. This can lead to notfication callback objects
	 * remaining linked even after disabling notifcation, leading later (after enabling notifications again) to multiple callbacks.
	 * Notification based read
	 */
	@Override
	public byte[] NotificationRead(String deviceAddress, Map<String, String> profile) throws DBusException {
		final CountDownLatch latch = new CountDownLatch(1);
		final byte[][] result = new byte[1][];
		BluetoothDevice device;
		try {
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						synchronized (gattChar) {
							if (gattChar != null) {
								if (!gattChar.getNotifying()) {
									gattChar.enableValueNotifications(new BluetoothNotification<byte[]>() {
										@Override
										public void run(byte[] data) {
											result[0] = data;
											latch.countDown();
										}
									});
									latch.await();
									gattChar.disableValueNotifications();
									return result[0];
								} else {
									return gattChar.readValue();
								}
							} else {
								logger.error("The device does not have {} gatt characterstics",
										profile.get(GATT_SERVICE));
							}
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to read data ", e);
			throw new DBusException("Failed to read data");
		}
		return null;
	}

	 
	private class AddressProfile extends Vector<Object> {
		public AddressProfile(String a, Map<String,String> p) {
			add(a);
			add(p);
		}
	}

	private Map<AddressProfile, BluetoothGattCharacteristic> subscriptions = new HashMap< AddressProfile, BluetoothGattCharacteristic>();

	/**
	 * @throws DBusException
	 * 
	 */
	@Override
	public void Subscribe(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			logger.info("Enabling notification on {}", deviceAddress);
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						if (gattChar != null) {
							if(!gattChar.getNotifying()){
								gattChar.enableValueNotifications(new NewRecordNotification(deviceAddress,profile));
								subscriptions.put(new AddressProfile(deviceAddress, profile), gattChar);
							}
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to subscribe ", e);
			throw new DBusException("Failed to subscribe");
		}
	}
	
	
	@Override
	public void Unsubscribe(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			logger.info("Disabling notification on {}", deviceAddress);
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						//BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						BluetoothGattCharacteristic gattChar = subscriptions.remove(new AddressProfile(deviceAddress, profile));
						if (gattChar != null) {
							if(gattChar.getNotifying()){
								logger.info("disabled");
								gattChar.disableValueNotifications();
							}
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to unsubscribe ", e);
			throw new DBusException("Failed to unsubscribe");
		}
	}

	/**
	 * 
	 * @author dagi
	 * 
	 *         New record signal for Subscription
	 */
	protected class NewRecordNotification implements BluetoothNotification<byte[]> {
		private final String address;
		
		private final Map<String, String> profile;

		public NewRecordNotification(String address, Map<String, String> profile) {
			this.address = address;
			this.profile = profile;
		}
		
		@Override
		public void run(byte[] record) {
			lastRecord = record;
 			try {
				Protocol.NewRecordSignal newRecordSignal = new Protocol.NewRecordSignal(AGILE_NEW_RECORD_SIGNAL_PATH,
						lastRecord, address, profile);
				logger.debug("Notifying {}", this);
				logger.info("data:" + byteArrayToHex(record) );
				connection.sendSignal(newRecordSignal);
			} catch (DBusException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 	Check the status of a device 
	 * @param deviceAddress
	 * @return
	 */
	@Override
	public StatusType DeviceStatus(String deviceAddress){
		StatusType ret;
		try {
			if(((BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null)).getConnected()){
				ret = new StatusType(DeviceStatusType.CONNECTED.toString());
			}else{
				ret = new StatusType(DeviceStatusType.DISCONNECTED.toString());
			}
		} catch (Exception e) {
			logger.error("Error on checking device status {}", e.getMessage());
			ret = new StatusType(DeviceStatusType.ERROR.toString());
		}

		return ret;
	}
	
    protected class NewNotificationRssi implements BluetoothNotification<Short> {
        BluetoothDevice device;
        DeviceOverview deviceOverview;

        public NewNotificationRssi (BluetoothDevice device){
            logger.info("rssi callback" + device.getName());
            this.device=device;            
        }

        @Override
        public void run(Short rssi) {
            logger.info(device.getName()+" new rssi:" + rssi.toString() );
            DeviceOverview updatedDeviceOverview= new DeviceOverview(device.getAddress(), AGILE_BLUETOOTH_BUS_NAME,
							device.getName(), AVAILABLE , String.valueOf(System.currentTimeMillis()));   
            try {
			    ProtocolManager.UpdateDeviceSignal foundDevSig = new ProtocolManager.UpdateDeviceSignal(
					AGILE_EXISTING_DEVICE_SIGNAL_PATH, updatedDeviceOverview);
			    connection.sendSignal(foundDevSig);
			} catch (DBusException e) {
				e.printStackTrace();
			}
        }
        public BluetoothDevice getDevice(){
            return device;
        }
    }

    public void disableRssiNotifications(BluetoothDevice device1){
        if (device1 != null) {
            device1.disableRSSINotifications();
        } else {
            if (bleDevices != null && bleDevices.size() > 0)            
			    for (BluetoothDevice device : bleDevices) {
                    logger.info("Disable rssi "+device.getName());
                    device.disableRSSINotifications();
                }
        }
 
    }

	// =========================UTILITY ==============

	public boolean isRemote() {
		return false;
	}
	
	void printDevice(BluetoothDevice device) {
		logger.info("Name = {}", device.getName());
		logger.info("Address = {}", device.getAddress());
		logger.info("Connected= {}", device.getConnected());
	}

	@Override
	public void finalize() {
		connection.disconnect();
        disableRssiNotifications(null);
	}

	/**
	 * Check if the device is newly discovered device
	 * 
	 * @param device
	 * @return
	 */
	private boolean isNewDevice(DeviceOverview device) {
		for (DeviceOverview dev : deviceList) {
			if (dev.getId().equals(device.getId())) {
				return false;
			}
		}
		return true;
	}

	private String byteArrayToHex(byte[] a) {
		 StringBuilder sb = new StringBuilder(a.length * 2);
		 for(byte b: a)
			sb.append(String.format("%02x", b));
		 return sb.toString();
	}

}
