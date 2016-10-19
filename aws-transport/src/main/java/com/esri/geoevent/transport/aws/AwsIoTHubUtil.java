/*
  Copyright 1995-2016s Esri

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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

public class AwsIoTHubUtil
{
  public static class KeyStorePasswordPair
  {
    public KeyStore keyStore;
    public String   keyPassword;

    public KeyStorePasswordPair(KeyStore keyStore, String keyPassword)
    {
      this.keyStore = keyStore;
      this.keyPassword = keyPassword;
    }
  }

  public static KeyStorePasswordPair getKeyStorePasswordPair(String certificateFile, String privateKeyFile)
  {
    return getKeyStorePasswordPair(certificateFile, privateKeyFile, null);
  }

  public static KeyStorePasswordPair getKeyStorePasswordPair(String certificateFile, String privateKeyFile, String keyAlgorithm)
  {
    if (certificateFile == null || privateKeyFile == null)
    {
      System.out.println("Certificate or private key file missing");
      return null;
    }

    Certificate certificate = loadCertificateFromFile(certificateFile);
    PrivateKey privateKey = loadPrivateKeyFromFile(privateKeyFile, keyAlgorithm);
    if (certificate == null || privateKey == null)
    {
      return null;
    }

    return getKeyStorePasswordPair(certificate, privateKey);
  }

  public static KeyStorePasswordPair getKeyStorePasswordPair(Certificate certificate, PrivateKey privateKey)
  {
    KeyStore keyStore = null;
    String keyPassword = null;
    try
    {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null);
      keyStore.setCertificateEntry("alias", certificate);

      // randomly generated key password for the key in the KeyStore
      keyPassword = new BigInteger(128, new SecureRandom()).toString(32);
      keyStore.setKeyEntry("alias", privateKey, keyPassword.toCharArray(), new Certificate[] { certificate });
    }
    catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e)
    {
      System.out.println("Failed to create key store");
      return null;
    }

    return new KeyStorePasswordPair(keyStore, keyPassword);
  }

  private static Certificate loadCertificateFromFile(String filename)
  {
    Certificate certificate = null;

    File file = new File(filename);
    if (!file.exists())
    {
      System.out.println("Certificate file not found: " + filename);
      return null;
    }
    try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file)))
    {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      certificate = certFactory.generateCertificate(stream);
    }
    catch (IOException | CertificateException e)
    {
      System.out.println("Failed to load certificate file " + filename);
    }

    return certificate;
  }

  private static PrivateKey loadPrivateKeyFromFile(String filename, String algorithm)
  {
    PrivateKey privateKey = null;

    File file = new File(filename);
    if (!file.exists())
    {
      System.out.println("Private key file not found: " + filename);
      return null;
    }
    try (DataInputStream stream = new DataInputStream(new FileInputStream(file)))
    {
      privateKey = AwsIoTHubPrivateKeyReader.getPrivateKey(stream, algorithm);
    }
    catch (IOException | GeneralSecurityException e)
    {
      System.out.println("Failed to load private key from file " + filename);
    }

    return privateKey;
  }
}
