/*
  Copyright 1995-2016 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

package com.esri.geoevent.transport.aws;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.esri.geoevent.transport.aws.AwsIoTHubUtil.KeyStorePasswordPair;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.component.RunningException;
import com.esri.ges.core.component.RunningState;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.transport.OutboundTransportBase;
import com.esri.ges.transport.TransportDefinition;
import com.esri.ges.util.Validator;

public class AwsIoTHubOutboundTransport extends OutboundTransportBase
{
  // logger
  private static final BundleLogger LOGGER                 = BundleLoggerFactory.getLogger(AwsIoTHubOutboundTransport.class);

  // transport properties
  private String                    iotServiceType         = "";
  private String                    thingName              = "";

  private String                    clientEndpoint         = "";
  private String                    x509Certificate        = "";
  private String                    privateKey             = "";
  private String                    topicName              = "";

  private volatile boolean          propertiesNeedUpdating = false;

  private boolean                   isEventHubType         = true;

  // device id client and receiver
  private AwsIoTHubDevice           geIoTDevice            = null;
  // event hub client
  private String                    clientId               = null;
  private AWSIotMqttClient          awsClient              = null;
  private AWSIotMessage             iotMessage             = null;

  public enum AwsIoTServiceType
  {
    IOT_TOPIC, IOT_DEVICE
  };

  public AwsIoTHubOutboundTransport(TransportDefinition definition) throws ComponentException
  {
    super(definition);
  }

  @Override
  public void start() throws RunningException
  {
    try
    {
      setRunningState(RunningState.STARTING);
      connectToAwsEventHub();
      setRunningState(RunningState.STARTED);

    }
    catch (Exception e)
    {
      LOGGER.error("INIT_ERROR", e);
      setErrorMessage(e.getMessage());
      setRunningState(RunningState.ERROR);
    }
  }

  private void applyProperties()
  {
    boolean somethingChanged = false;

    if (hasProperty("iotservicetype"))
    {
      // IoT Service Type
      String newIotServiceType = getProperty("iotservicetype").getValueAsString();
      if (!iotServiceType.equals(newIotServiceType))
      {
        iotServiceType = newIotServiceType;
        somethingChanged = true;
      }
    }
    // Device Id Field Name
    if (hasProperty("thingName"))
    {
      String newThingName = getProperty("thingName").getValueAsString();
      if (!thingName.equals(newThingName))
      {
        thingName = newThingName;
        somethingChanged = true;
      }
    }
    // Client End point
    if (hasProperty("endpoint"))
    {
      String newClientEndpoint = getProperty("endpoint").getValueAsString();
      if (!clientEndpoint.equals(newClientEndpoint))
      {
        clientEndpoint = newClientEndpoint;
        somethingChanged = true;
      }
    }
    // X.509 certificate
    if (hasProperty("X509certificate"))
    {
      String newX509Cert = getProperty("X509certificate").getValueAsString();
      if (!x509Certificate.equals(newX509Cert))
      {
        x509Certificate = newX509Cert;
        somethingChanged = true;
      }
    }
    // private key
    if (hasProperty("privateKey"))
    {
      String newPrivateKey = getProperty("privateKey").getValueAsString();
      if (!privateKey.equals(newPrivateKey))
      {
        privateKey = newPrivateKey;
        somethingChanged = true;
      }
    }
    // topic name
    if (hasProperty("topic"))
    {
      String newTopicName = getProperty("topic").getValueAsString();
      if (!topicName.equals(newTopicName))
      {
        topicName = newTopicName;
        somethingChanged = true;
      }
    }

    propertiesNeedUpdating = somethingChanged;
  }

  private void connectToAwsEventHub() throws AWSIotException
  {
    applyProperties();
    if (propertiesNeedUpdating)
    {
      cleanup();
      propertiesNeedUpdating = false;
    }

    // iot service type - IOT_TOPIC | IOT_DEVICE
    isEventHubType = AwsIoTServiceType.IOT_TOPIC.toString().equals(iotServiceType);

    // get KeyStore credentials
    KeyStorePasswordPair pair = AwsIoTHubUtil.getKeyStorePasswordPair(x509Certificate, privateKey, null);

    // create AwsClient
    clientId = String.format("%s-%s", thingName, new BigInteger(128, new SecureRandom()).toString(32));
    awsClient = new AWSIotMqttClient(clientEndpoint, clientId, pair.keyStore, pair.keyPassword);

    // attach device
    if (!isEventHubType)
    {
      // IoT Device attach
      geIoTDevice = new AwsIoTHubDevice(thingName);
      LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": Attaching device:" + geIoTDevice.getThingName());
      awsClient.attach(geIoTDevice);
    }

    // connect to Aws IoT Hub
    LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": Connecting");
    awsClient.connect();
    LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": Connected");

    // geIoTDevice.delete(10000); // delete shadow
  }

  @Override
  public synchronized void stop()
  {
    setRunningState(RunningState.STOPPING);
    cleanup();
    setRunningState(RunningState.STOPPED);
  }

  private void cleanup()
  {
    // clean up the aws hub client
    try
    {
      if (awsClient != null)
      {
        if (geIoTDevice != null)
        {
          LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": detaching device: " + geIoTDevice.getThingName());
          awsClient.detach(geIoTDevice);
          LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": detached device: " + geIoTDevice.getThingName());
          // geIoTDevice.delete(5000); // delete shadow
        }
        LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": disconnecting");
        awsClient.disconnect(5000);
        LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": disconnected");
      }
    }
    catch (Exception e)
    {
      LOGGER.error("CLEANUP_ERROR", e);
      setErrorMessage(e.getMessage());
    }
    finally
    {
      geIoTDevice = null;
      awsClient = null;
    }
  }

  @Override
  public void receive(ByteBuffer buffer, String channelId)
  {
    try
    {
      // Send Event to an Event Hub
      String message = new String(buffer.array(), StandardCharsets.UTF_8);
      byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
      iotMessage = new AWSIoTPublishListener(topicName, AWSIotQos.QOS0, bytes);

      if (isEventHubType)
      {
        if (awsClient != null)
        {
          LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": publishing to topic : " + this.topicName + ", message:  >>> " + message);
          awsClient.publish(iotMessage);
        }
        else
        {
          LOGGER.warn("FAILED_TO_SEND_INVALID_EH_CONNECTION", clientEndpoint);
        }
      }
      else
      {
        // update shadow
        if (thingName != null & Validator.isNotBlank(thingName))
        {
          // geIoTDevice.delete(); // delete shadow
          LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": updating the device state");
          geIoTDevice.update(iotMessage, 10000); // update device state
        }
        else
        {
          LOGGER.warn("FAILED_TO_SEND_INVALID_DEVICE_ID", thingName);
        }
      }
    }
    catch (Exception e)
    {
      // streamClient.stop();
      LOGGER.error(e.getMessage(), e);
      setErrorMessage(e.getMessage());
      setRunningState(RunningState.ERROR);
    }
  }

  /**
   * AWSIoTPublishListener class extends {@link AWSIotMessage} to publish
   * messages to a topic.
   */
  private final class AWSIoTPublishListener extends AWSIotMessage
  {

    public AWSIoTPublishListener(String topic, AWSIotQos qos, byte[] payload)
    {
      super(topic, qos, payload);
    }

    @Override
    public void onSuccess()
    {
      LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": publish success for: " + this.topic + " >>> " + getStringPayload());
    }

    @Override
    public void onFailure()
    {
      LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": publish failed for: " + this.topic + " >>> " + getStringPayload());
    }

    @Override
    public void onTimeout()
    {
      LOGGER.info(System.currentTimeMillis() + ": ClientId: " + clientId + ": publish timeout for: " + this.topic + " >>> " + getStringPayload());
    }

  }

}
