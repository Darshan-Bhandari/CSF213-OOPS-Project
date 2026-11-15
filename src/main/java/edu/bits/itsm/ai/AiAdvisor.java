package edu.bits.itsm.ai;

import edu.bits.itsm.identity.AccountPrincipal;

public interface AiAdvisor {
  void suggest(long incident, long version, String kind, AccountPrincipal actor);
}
