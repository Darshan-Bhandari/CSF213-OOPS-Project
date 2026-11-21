package edu.bits.itsm.identity;

public interface RecoveryDelivery {
  void send(String email, String link);
}
