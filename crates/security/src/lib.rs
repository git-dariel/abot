use aes_gcm::aead::{Aead, Payload};
use aes_gcm::{Aes256Gcm, KeyInit, Nonce};
use ed25519_dalek::{Signature, Signer, SigningKey, Verifier, VerifyingKey};
use rand::{rngs::OsRng, RngCore};
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;

pub const SESSION_TOKEN_BYTES: usize = 32;
pub const IDENTITY_PUBLIC_KEY_BYTES: usize = 32;
pub const IDENTITY_SIGNATURE_BYTES: usize = 64;
pub const SESSION_KEY_BYTES: usize = 32;
pub const AEAD_NONCE_BYTES: usize = 12;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EncryptedFrame {
    pub nonce: [u8; AEAD_NONCE_BYTES],
    pub ciphertext: Vec<u8>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DecryptionError;

pub fn encrypt_frame(
    key: &[u8; SESSION_KEY_BYTES],
    plaintext: &[u8],
    associated_data: &[u8],
) -> EncryptedFrame {
    let cipher = Aes256Gcm::new_from_slice(key).expect("fixed-size AES key");
    let mut nonce = [0_u8; AEAD_NONCE_BYTES];
    OsRng.fill_bytes(&mut nonce);
    let ciphertext = cipher
        .encrypt(
            Nonce::from_slice(&nonce),
            Payload {
                msg: plaintext,
                aad: associated_data,
            },
        )
        .expect("AES-GCM encryption with valid inputs");
    EncryptedFrame { nonce, ciphertext }
}

pub fn decrypt_frame(
    key: &[u8; SESSION_KEY_BYTES],
    nonce: &[u8; AEAD_NONCE_BYTES],
    ciphertext: &[u8],
    associated_data: &[u8],
) -> Result<Vec<u8>, DecryptionError> {
    let cipher = Aes256Gcm::new_from_slice(key).expect("fixed-size AES key");
    cipher
        .decrypt(
            Nonce::from_slice(nonce),
            Payload {
                msg: ciphertext,
                aad: associated_data,
            },
        )
        .map_err(|_| DecryptionError)
}

pub struct DeviceIdentity {
    signing_key: SigningKey,
}

impl DeviceIdentity {
    pub fn generate() -> Self {
        Self {
            signing_key: SigningKey::generate(&mut OsRng),
        }
    }

    pub fn from_secret_bytes(bytes: &[u8; 32]) -> Self {
        Self {
            signing_key: SigningKey::from_bytes(bytes),
        }
    }

    pub fn secret_bytes(&self) -> [u8; 32] {
        self.signing_key.to_bytes()
    }

    pub fn public_key(&self) -> [u8; IDENTITY_PUBLIC_KEY_BYTES] {
        self.signing_key.verifying_key().to_bytes()
    }

    pub fn sign(&self, message: &[u8]) -> [u8; IDENTITY_SIGNATURE_BYTES] {
        self.signing_key.sign(message).to_bytes()
    }
}

impl Default for DeviceIdentity {
    fn default() -> Self {
        Self::generate()
    }
}

pub fn verify_identity_signature(
    public_key: &[u8; IDENTITY_PUBLIC_KEY_BYTES],
    message: &[u8],
    signature: &[u8; IDENTITY_SIGNATURE_BYTES],
) -> bool {
    let Ok(verifying_key) = VerifyingKey::from_bytes(public_key) else {
        return false;
    };
    verifying_key
        .verify(message, &Signature::from_bytes(signature))
        .is_ok()
}

pub fn generate_session_token() -> [u8; SESSION_TOKEN_BYTES] {
    let mut token = [0_u8; SESSION_TOKEN_BYTES];
    OsRng.fill_bytes(&mut token);
    token
}

pub fn fingerprint(public_identity: &[u8]) -> [u8; 32] {
    Sha256::digest(public_identity).into()
}

pub fn token_matches(expected: &[u8; SESSION_TOKEN_BYTES], supplied: &[u8]) -> bool {
    supplied.len() == SESSION_TOKEN_BYTES && bool::from(expected.as_slice().ct_eq(supplied))
}

pub fn pairing_code(binding_material: &[u8]) -> String {
    let digest = Sha256::digest(binding_material);
    let value = u32::from_be_bytes(digest[..4].try_into().expect("four-byte slice")) % 1_000_000;
    format!("{value:06}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pairing_code_is_stable_six_digits() {
        let code = pairing_code(b"identity-bound transcript");
        assert_eq!(code.len(), 6);
        assert!(code.chars().all(|character| character.is_ascii_digit()));
        assert_eq!(code, pairing_code(b"identity-bound transcript"));
    }

    #[test]
    fn token_comparison_requires_exact_bytes() {
        let expected = [7_u8; SESSION_TOKEN_BYTES];
        assert!(token_matches(&expected, &expected));
        assert!(!token_matches(&expected, &[7_u8; SESSION_TOKEN_BYTES - 1]));
    }

    #[test]
    fn identity_signatures_reject_tampering() {
        let identity = DeviceIdentity::generate();
        let signature = identity.sign(b"pairing transcript");

        assert!(verify_identity_signature(
            &identity.public_key(),
            b"pairing transcript",
            &signature
        ));
        assert!(!verify_identity_signature(
            &identity.public_key(),
            b"tampered transcript",
            &signature
        ));
    }

    #[test]
    fn encrypted_frames_require_the_key_and_associated_data() {
        let key = [7_u8; SESSION_KEY_BYTES];
        let frame = encrypt_frame(&key, b"private metadata", b"offer-v1");

        assert_eq!(
            decrypt_frame(&key, &frame.nonce, &frame.ciphertext, b"offer-v1").unwrap(),
            b"private metadata"
        );
        assert!(decrypt_frame(&key, &frame.nonce, &frame.ciphertext, b"other").is_err());
        assert!(decrypt_frame(
            &[8_u8; SESSION_KEY_BYTES],
            &frame.nonce,
            &frame.ciphertext,
            b"offer-v1"
        )
        .is_err());
    }
}
