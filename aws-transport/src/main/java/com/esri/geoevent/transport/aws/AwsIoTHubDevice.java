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

import java.util.Random;

import com.amazonaws.services.iot.client.AWSIotDevice;
import com.amazonaws.services.iot.client.AWSIotDeviceProperty;

/**
 * This class encapsulates an actual device. It extends {@link AWSIotDevice} to
 * define properties that are to be kept in sync with the AWS IoT shadow.
 */
public class AwsIoTHubDevice extends AWSIotDevice
{
  private enum MyBulbStatus
  {
    ON(0), OFF(1), BLINK(2), UNKNOWN(3);
    private int _state;

    private MyBulbStatus(int state)
    {
      _state = state;
    }

    int getState()
    {
      return _state;
    }
  }

  public AwsIoTHubDevice(String thingName)
  {
    super(thingName);
  }

  @AWSIotDeviceProperty
  private String bulbState;

  public String getBulbState()
  {
    // get bulb state from gpio
    // get a random state
    String _defaultRet = MyBulbStatus.UNKNOWN.toString();
    int _state = new Random().nextInt(4);
    for (MyBulbStatus s : MyBulbStatus.values())
    {
      if (s.getState() == _state)
      {
        _defaultRet = s.toString();
        break;
      }
    }
    //System.out.println(System.currentTimeMillis() + " >>> reported bulb state: " + (_defaultRet));
    return _defaultRet;
  }

  public void setBulbState(String desiredState)
  {
    //System.out.println(System.currentTimeMillis() + " <<< desired bulb state to " + (desiredState));
    this.bulbState = MyBulbStatus.valueOf(desiredState).toString();
  }
}
