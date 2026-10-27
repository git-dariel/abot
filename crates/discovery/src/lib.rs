use mdns_sd::{ServiceDaemon, ServiceEvent, ServiceInfo};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::{IpAddr, SocketAddr};
use std::time::{Duration, Instant};
use thiserror::Error;
use uuid::Uuid;

pub const SERVICE_TYPE: &str = "_airbridge._tcp.local.";

/// A discovery record is untrusted input. It may be displayed as a candidate
/// but must never be used as proof of a paired identity.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DiscoveredPeer {
    pub instance_id: Uuid,
    pub display_name: String,
    pub address: SocketAddr,
    pub protocol_major: u16,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum DiscoveryError {
    #[error("display name is empty or too long")]
    InvalidDisplayName,
    #[error("only private, link-local, or loopback addresses are permitted")]
    NonLocalAddress,
    #[error("protocol major version is unsupported")]
    UnsupportedProtocol,
    #[error("manual endpoint is invalid")]
    InvalidEndpoint,
    #[error("mDNS operation failed: {0}")]
    Mdns(#[from] mdns_sd::Error),
}

impl DiscoveredPeer {
    pub fn validate(&self, supported_protocol_major: u16) -> Result<(), DiscoveryError> {
        if self.display_name.trim().is_empty() || self.display_name.len() > 80 {
            return Err(DiscoveryError::InvalidDisplayName);
        }
        if self.protocol_major != supported_protocol_major {
            return Err(DiscoveryError::UnsupportedProtocol);
        }
        if !is_usable_local_address(self.address.ip()) {
            return Err(DiscoveryError::NonLocalAddress);
        }
        Ok(())
    }
}

pub fn is_usable_local_address(address: IpAddr) -> bool {
    match address {
        IpAddr::V4(address) => {
            address.is_private() || address.is_link_local() || address.is_loopback()
        }
        IpAddr::V6(address) => {
            address.is_unique_local() || address.is_unicast_link_local() || address.is_loopback()
        }
    }
}

pub struct ServiceAdvertisement {
    daemon: ServiceDaemon,
    fullname: String,
}

impl ServiceAdvertisement {
    pub fn start(
        device_id: Uuid,
        display_name: &str,
        address: IpAddr,
        port: u16,
        protocol_major: u16,
    ) -> Result<Self, DiscoveryError> {
        let candidate = DiscoveredPeer {
            instance_id: device_id,
            display_name: display_name.to_owned(),
            address: SocketAddr::new(address, port),
            protocol_major,
        };
        candidate.validate(protocol_major)?;

        let daemon = ServiceDaemon::new()?;
        let instance_name = format!("airbridge-{device_id}");
        let host_name = format!("{instance_name}.local.");
        let properties = HashMap::from([
            ("device_id".to_owned(), device_id.to_string()),
            ("display_name".to_owned(), display_name.to_owned()),
            ("protocol_major".to_owned(), protocol_major.to_string()),
        ]);
        let service = ServiceInfo::new(
            SERVICE_TYPE,
            &instance_name,
            &host_name,
            address,
            port,
            properties,
        )?;
        let fullname = service.get_fullname().to_owned();
        daemon.register(service)?;
        Ok(Self { daemon, fullname })
    }
}

impl Drop for ServiceAdvertisement {
    fn drop(&mut self) {
        let _ = self.daemon.unregister(&self.fullname);
        let _ = self.daemon.shutdown();
    }
}

pub fn discover_for(
    timeout: Duration,
    supported_protocol_major: u16,
) -> Result<Vec<DiscoveredPeer>, DiscoveryError> {
    let daemon = ServiceDaemon::new()?;
    let receiver = daemon.browse(SERVICE_TYPE)?;
    let deadline = Instant::now() + timeout;
    let mut peers = HashMap::new();

    while let Some(remaining) = deadline.checked_duration_since(Instant::now()) {
        let Ok(event) = receiver.recv_timeout(remaining) else {
            break;
        };
        let ServiceEvent::ServiceResolved(service) = event else {
            continue;
        };
        let Some(device_id) = service
            .get_property_val_str("device_id")
            .and_then(|value| Uuid::parse_str(value).ok())
        else {
            continue;
        };
        let Some(display_name) = service.get_property_val_str("display_name") else {
            continue;
        };
        let Some(protocol_major) = service
            .get_property_val_str("protocol_major")
            .and_then(|value| value.parse::<u16>().ok())
        else {
            continue;
        };
        let Some(address) = service
            .get_addresses()
            .iter()
            .map(|address| address.to_ip_addr())
            .find(|address| is_usable_local_address(*address))
        else {
            continue;
        };
        let peer = DiscoveredPeer {
            instance_id: device_id,
            display_name: display_name.to_owned(),
            address: SocketAddr::new(address, service.get_port()),
            protocol_major,
        };
        if peer.validate(supported_protocol_major).is_ok() {
            peers.insert(device_id, peer);
        }
    }

    let _ = daemon.stop_browse(SERVICE_TYPE);
    let _ = daemon.shutdown();
    Ok(peers.into_values().collect())
}

pub fn parse_manual_endpoint(input: &str, default_port: u16) -> Result<SocketAddr, DiscoveryError> {
    let trimmed = input.trim();
    let endpoint = trimmed
        .parse::<SocketAddr>()
        .or_else(|_| {
            trimmed
                .parse::<IpAddr>()
                .map(|address| SocketAddr::new(address, default_port))
        })
        .map_err(|_| DiscoveryError::InvalidEndpoint)?;
    if !is_usable_local_address(endpoint.ip()) {
        return Err(DiscoveryError::NonLocalAddress);
    }
    Ok(endpoint)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_public_discovery_addresses() {
        let peer = DiscoveredPeer {
            instance_id: Uuid::nil(),
            display_name: "Peer".into(),
            address: "8.8.8.8:47741".parse().unwrap(),
            protocol_major: 1,
        };
        assert_eq!(peer.validate(1), Err(DiscoveryError::NonLocalAddress));
    }

    #[test]
    fn manual_endpoint_supports_local_ip_with_default_port() {
        assert_eq!(
            parse_manual_endpoint("192.168.1.24", 47741).unwrap(),
            "192.168.1.24:47741".parse().unwrap()
        );
        assert_eq!(
            parse_manual_endpoint("8.8.8.8", 47741),
            Err(DiscoveryError::NonLocalAddress)
        );
        assert_eq!(
            parse_manual_endpoint("not-an-address", 47741),
            Err(DiscoveryError::InvalidEndpoint)
        );
    }
}
