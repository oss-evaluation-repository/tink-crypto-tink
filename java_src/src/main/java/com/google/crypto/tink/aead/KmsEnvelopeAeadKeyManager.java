// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.aead;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.KmsClients;
import com.google.crypto.tink.Registry;
import com.google.crypto.tink.TinkProtoParametersFormat;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.internal.PrimitiveFactory;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.KmsEnvelopeAeadKey;
import com.google.crypto.tink.proto.KmsEnvelopeAeadKeyFormat;
import com.google.crypto.tink.subtle.Validators;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.GeneralSecurityException;

/**
 * This key manager generates new {@code KmsEnvelopeAeadKey} keys and produces new instances of
 * {@code KmsEnvelopeAead}.
 */
public class KmsEnvelopeAeadKeyManager extends KeyTypeManager<KmsEnvelopeAeadKey> {
  private static final String TYPE_URL =
      "type.googleapis.com/google.crypto.tink.KmsEnvelopeAeadKey";

  KmsEnvelopeAeadKeyManager() {
    super(
        KmsEnvelopeAeadKey.class,
        new PrimitiveFactory<Aead, KmsEnvelopeAeadKey>(Aead.class) {
          @Override
          public Aead getPrimitive(KmsEnvelopeAeadKey keyProto) throws GeneralSecurityException {
            String keyUri = keyProto.getParams().getKekUri();
            KmsClient kmsClient = KmsClients.get(keyUri);
            Aead remote = kmsClient.getAead(keyUri);
            return new KmsEnvelopeAead(keyProto.getParams().getDekTemplate(), remote);
          }
        });
  }

  @Override
  public String getKeyType() {
    return TYPE_URL;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public KeyMaterialType keyMaterialType() {
    return KeyMaterialType.REMOTE;
  }

  @Override
  public void validateKey(KmsEnvelopeAeadKey key) throws GeneralSecurityException {
    Validators.validateVersion(key.getVersion(), getVersion());
    if (!KmsEnvelopeAead.isSupportedDekKeyType(key.getParams().getDekTemplate().getTypeUrl())) {
      throw new GeneralSecurityException(
          "Unsupported DEK key type: "
              + key.getParams().getDekTemplate().getTypeUrl()
              + ". Only Tink AEAD key types are supported.");
    }
  }

  @Override
  public KmsEnvelopeAeadKey parseKey(ByteString byteString) throws InvalidProtocolBufferException {
    return KmsEnvelopeAeadKey.parseFrom(byteString, ExtensionRegistryLite.getEmptyRegistry());
  }

  @Override
  public KeyFactory<KmsEnvelopeAeadKeyFormat, KmsEnvelopeAeadKey> keyFactory() {
    return new KeyFactory<KmsEnvelopeAeadKeyFormat, KmsEnvelopeAeadKey>(
        KmsEnvelopeAeadKeyFormat.class) {
      @Override
      public void validateKeyFormat(KmsEnvelopeAeadKeyFormat format)
          throws GeneralSecurityException {
        if (!KmsEnvelopeAead.isSupportedDekKeyType(format.getDekTemplate().getTypeUrl())) {
          throw new GeneralSecurityException(
              "Unsupported DEK key type: "
                  + format.getDekTemplate().getTypeUrl()
                  + ". Only Tink AEAD key types are supported.");
        }
        if (format.getKekUri().isEmpty() || !format.hasDekTemplate()) {
          throw new GeneralSecurityException("invalid key format: missing KEK URI or DEK template");
        }
      }

      @Override
      public KmsEnvelopeAeadKeyFormat parseKeyFormat(ByteString byteString)
          throws InvalidProtocolBufferException {
        return KmsEnvelopeAeadKeyFormat.parseFrom(
            byteString, ExtensionRegistryLite.getEmptyRegistry());
      }

      @Override
      public KmsEnvelopeAeadKey createKey(KmsEnvelopeAeadKeyFormat format)
          throws GeneralSecurityException {
        return KmsEnvelopeAeadKey.newBuilder().setParams(format).setVersion(getVersion()).build();
      }
    };
  }

  /**
   * Returns a new {@link KeyTemplate} that can generate a {@link
   * com.google.crypto.tink.proto.KmsEnvelopeAeadKey} whose key encrypting key (KEK) is pointing to
   * {@code kekUri} and DEK template is {@code dekTemplate}. Keys generated by this key template
   * uses RAW output prefix to make them compatible with the remote KMS' encrypt/decrypt operations.
   * Unlike other templates, when you call {@link KeysetHandle#generateNew} with this template, Tink
   * does not generate new key material, but only creates a reference to the remote KEK.
   */
  public static KeyTemplate createKeyTemplate(String kekUri, KeyTemplate dekTemplate) {
    try {
      KmsEnvelopeAeadKeyFormat format = createKeyFormat(kekUri, dekTemplate);
      return KeyTemplate.create(TYPE_URL, format.toByteArray(), KeyTemplate.OutputPrefixType.RAW);
    } catch (GeneralSecurityException | InvalidProtocolBufferException e) {
      // It is in principle possible that this throws: if the "KeyTemplate" is created directly
      // from a parameters object, but then we cannot serialize it.  However, the only way I can
      // see this happen is if a user defines their own parameters object and then passes it in
      // here, hence I think an IllegalArgumentError is appropriate.
      throw new IllegalArgumentException("Unable to serialize key template", e);
    }
  }

  public static void register(boolean newKeyAllowed) throws GeneralSecurityException {
    Registry.registerKeyManager(new KmsEnvelopeAeadKeyManager(), newKeyAllowed);
  }

  static KmsEnvelopeAeadKeyFormat createKeyFormat(String kekUri, KeyTemplate dekTemplate)
      throws GeneralSecurityException, InvalidProtocolBufferException {
    if (!KmsEnvelopeAead.isSupportedDekKeyType(dekTemplate.getTypeUrl())) {
      throw new IllegalArgumentException(
          "Unsupported DEK key type: "
              + dekTemplate.getTypeUrl()
              + ". Only Tink AEAD key types are supported.");
    }
    byte[] serializedTemplate = TinkProtoParametersFormat.serialize(dekTemplate.toParameters());
    return KmsEnvelopeAeadKeyFormat.newBuilder()
        .setDekTemplate(
            com.google.crypto.tink.proto.KeyTemplate.parseFrom(
                serializedTemplate, ExtensionRegistryLite.getEmptyRegistry()))
        .setKekUri(kekUri)
        .build();
  }
}
