// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

#ifndef TINK_AEAD_INTERNAL_CORD_AES_GCM_BORINGSSL_H_
#define TINK_AEAD_INTERNAL_CORD_AES_GCM_BORINGSSL_H_

#include <memory>

#include "absl/strings/string_view.h"
#include "openssl/aead.h"
#include "openssl/base.h"
#include "openssl/cipher.h"
#include "tink/aead/cord_aead.h"
#include "tink/util/secret_data.h"
#include "tink/util/status.h"
#include "tink/util/statusor.h"

namespace crypto {
namespace tink {

class CordAesGcmBoringSsl : public CordAead {
 public:
  static crypto::tink::util::StatusOr<std::unique_ptr<CordAead>> New(
      util::SecretData key_value);

  crypto::tink::util::StatusOr<absl::Cord> Encrypt(
      absl::Cord plaintext, absl::Cord additional_data) const override;

  crypto::tink::util::StatusOr<absl::Cord> Decrypt(
      absl::Cord ciphertext, absl::Cord additional_data) const override;

  ~CordAesGcmBoringSsl() override {}

 private:
  static constexpr int kIvSizeInBytes = 12;
  static constexpr int kTagSizeInBytes = 16;

  CordAesGcmBoringSsl() {}
  crypto::tink::util::Status Init(crypto::tink::util::SecretData key_value);

  const EVP_CIPHER *cipher_;
  crypto::tink::util::SecretData key_;
};

}  // namespace tink
}  // namespace crypto

#endif  // TINK_AEAD_INTERNAL_CORD_AES_GCM_BORINGSSL_H_
