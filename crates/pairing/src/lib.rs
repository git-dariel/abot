use airbridge_security::{pairing_code, verify_identity_signature, DeviceIdentity};
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use std::net::SocketAddr;
use thiserror::Error;
use uuid::Uuid;

pub const PAIRING_INVITATION_VERSION: u16 = 1;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingInvitation {
    pub version: u16,
    pub device_id: Uuid,
    pub display_name: String,
    pub endpoint: SocketAddr,
    pub expires_at_unix_ms: u64,
    pub nonce_hex: String,
    pub identity_key_hex: String,
    pub signature_hex: String,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum InvitationError {
    #[error("pairing invitation is malformed")]
    Malformed,
    #[error("pairing invitation has expired")]
    Expired,
    #[error("pairing invitation signature is invalid")]
    InvalidSignature,
    #[error("pairing invitation version is unsupported")]
    UnsupportedVersion,
}

impl PairingInvitation {
    pub fn create(
        identity: &DeviceIdentity,
        device_id: Uuid,
        display_name: String,
        endpoint: SocketAddr,
        expires_at_unix_ms: u64,
    ) -> Result<Self, InvitationError> {
        if display_name.trim().is_empty() || display_name.len() > 80 {
            return Err(InvitationError::Malformed);
        }
        let mut nonce = [0_u8; 32];
        OsRng.fill_bytes(&mut nonce);
        let identity_key = identity.public_key();
        let transcript = invitation_transcript(
            PAIRING_INVITATION_VERSION,
            device_id,
            &display_name,
            endpoint,
            expires_at_unix_ms,
            &nonce,
            &identity_key,
        )?;
        Ok(Self {
            version: PAIRING_INVITATION_VERSION,
            device_id,
            display_name,
            endpoint,
            expires_at_unix_ms,
            nonce_hex: hex::encode(nonce),
            identity_key_hex: hex::encode(identity_key),
            signature_hex: hex::encode(identity.sign(&transcript)),
        })
    }

    pub fn to_qr_payload(&self) -> Result<String, InvitationError> {
        serde_json::to_string(self).map_err(|_| InvitationError::Malformed)
    }

    pub fn from_qr_payload(payload: &str, now_unix_ms: u64) -> Result<Self, InvitationError> {
        let invitation: Self =
            serde_json::from_str(payload).map_err(|_| InvitationError::Malformed)?;
        invitation.verify(now_unix_ms)?;
        Ok(invitation)
    }

    pub fn verify(&self, now_unix_ms: u64) -> Result<(), InvitationError> {
        if self.version != PAIRING_INVITATION_VERSION {
            return Err(InvitationError::UnsupportedVersion);
        }
        if now_unix_ms > self.expires_at_unix_ms {
            return Err(InvitationError::Expired);
        }
        if self.display_name.trim().is_empty() || self.display_name.len() > 80 {
            return Err(InvitationError::Malformed);
        }
        let nonce: [u8; 32] = decode_fixed(&self.nonce_hex)?;
        let identity_key: [u8; 32] = decode_fixed(&self.identity_key_hex)?;
        let signature: [u8; 64] = decode_fixed(&self.signature_hex)?;
        let transcript = invitation_transcript(
            self.version,
            self.device_id,
            &self.display_name,
            self.endpoint,
            self.expires_at_unix_ms,
            &nonce,
            &identity_key,
        )?;
        if !verify_identity_signature(&identity_key, &transcript, &signature) {
            return Err(InvitationError::InvalidSignature);
        }
        Ok(())
    }

    pub fn comparison_code(&self) -> Result<String, InvitationError> {
        let nonce: [u8; 32] = decode_fixed(&self.nonce_hex)?;
        let identity_key: [u8; 32] = decode_fixed(&self.identity_key_hex)?;
        let transcript = invitation_transcript(
            self.version,
            self.device_id,
            &self.display_name,
            self.endpoint,
            self.expires_at_unix_ms,
            &nonce,
            &identity_key,
        )?;
        Ok(pairing_code(&transcript))
    }
}

fn decode_fixed<const N: usize>(value: &str) -> Result<[u8; N], InvitationError> {
    let bytes = hex::decode(value).map_err(|_| InvitationError::Malformed)?;
    bytes.try_into().map_err(|_| InvitationError::Malformed)
}

fn invitation_transcript(
    version: u16,
    device_id: Uuid,
    display_name: &str,
    endpoint: SocketAddr,
    expires_at_unix_ms: u64,
    nonce: &[u8; 32],
    identity_key: &[u8; 32],
) -> Result<Vec<u8>, InvitationError> {
    let endpoint = endpoint.to_string();
    let mut transcript = Vec::with_capacity(128);
    transcript.extend_from_slice(b"AIRBRIDGE-PAIRING\0");
    transcript.extend_from_slice(&version.to_be_bytes());
    transcript.extend_from_slice(device_id.as_bytes());
    append_field(&mut transcript, display_name.as_bytes())?;
    append_field(&mut transcript, endpoint.as_bytes())?;
    transcript.extend_from_slice(&expires_at_unix_ms.to_be_bytes());
    transcript.extend_from_slice(nonce);
    transcript.extend_from_slice(identity_key);
    Ok(transcript)
}

fn append_field(target: &mut Vec<u8>, value: &[u8]) -> Result<(), InvitationError> {
    let length = u16::try_from(value.len()).map_err(|_| InvitationError::Malformed)?;
    target.extend_from_slice(&length.to_be_bytes());
    target.extend_from_slice(value);
    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PairingState {
    Idle,
    CodeDisplayed,
    AwaitingLocalConsent,
    AwaitingRemoteConsent,
    Paired,
    Rejected,
    Expired,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PairingEvent {
    CreateCode,
    RemoteCodeVerified,
    LocalApprove,
    RemoteApprove,
    Reject,
    Expire,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PairingSession {
    pub id: Uuid,
    pub state: PairingState,
    local_approved: bool,
    remote_approved: bool,
}

#[derive(Debug, Error, PartialEq, Eq)]
#[error("event {event:?} is invalid while pairing is {state:?}")]
pub struct InvalidTransition {
    pub state: PairingState,
    pub event: PairingEvent,
}

impl PairingSession {
    pub fn new() -> Self {
        Self {
            id: Uuid::new_v4(),
            state: PairingState::Idle,
            local_approved: false,
            remote_approved: false,
        }
    }

    pub fn apply(&mut self, event: PairingEvent) -> Result<PairingState, InvalidTransition> {
        match (self.state, event) {
            (PairingState::Idle, PairingEvent::CreateCode) => {
                self.state = PairingState::CodeDisplayed
            }
            (PairingState::CodeDisplayed, PairingEvent::RemoteCodeVerified) => {
                self.state = PairingState::AwaitingLocalConsent
            }
            (PairingState::AwaitingLocalConsent, PairingEvent::LocalApprove) => {
                self.local_approved = true;
                self.state = PairingState::AwaitingRemoteConsent;
            }
            (PairingState::AwaitingRemoteConsent, PairingEvent::RemoteApprove) => {
                self.remote_approved = true;
                self.state = PairingState::Paired;
            }
            (
                PairingState::CodeDisplayed
                | PairingState::AwaitingLocalConsent
                | PairingState::AwaitingRemoteConsent,
                PairingEvent::Reject,
            ) => self.state = PairingState::Rejected,
            (
                PairingState::CodeDisplayed
                | PairingState::AwaitingLocalConsent
                | PairingState::AwaitingRemoteConsent,
                PairingEvent::Expire,
            ) => self.state = PairingState::Expired,
            _ => {
                return Err(InvalidTransition {
                    state: self.state,
                    event,
                })
            }
        }
        Ok(self.state)
    }

    pub fn is_mutually_approved(&self) -> bool {
        self.state == PairingState::Paired && self.local_approved && self.remote_approved
    }
}

impl Default for PairingSession {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn requires_both_explicit_approvals() {
        let mut session = PairingSession::new();
        session.apply(PairingEvent::CreateCode).unwrap();
        session.apply(PairingEvent::RemoteCodeVerified).unwrap();
        session.apply(PairingEvent::LocalApprove).unwrap();
        assert!(!session.is_mutually_approved());
        session.apply(PairingEvent::RemoteApprove).unwrap();
        assert!(session.is_mutually_approved());
    }

    #[test]
    fn signed_invitation_round_trips_and_rejects_tampering() {
        let identity = DeviceIdentity::generate();
        let invitation = PairingInvitation::create(
            &identity,
            Uuid::new_v4(),
            "AirBridge PC".into(),
            "192.168.1.10:47741".parse().unwrap(),
            20_000,
        )
        .unwrap();
        let payload = invitation.to_qr_payload().unwrap();
        let decoded = PairingInvitation::from_qr_payload(&payload, 10_000).unwrap();
        assert_eq!(decoded, invitation);
        assert_eq!(decoded.comparison_code().unwrap().len(), 6);

        let mut tampered = decoded;
        tampered.display_name = "Impostor".into();
        assert_eq!(
            tampered.verify(10_000),
            Err(InvitationError::InvalidSignature)
        );
        assert_eq!(invitation.verify(20_001), Err(InvitationError::Expired));
    }
}
