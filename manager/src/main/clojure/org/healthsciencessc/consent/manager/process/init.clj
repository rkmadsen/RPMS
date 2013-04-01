(ns org.healthsciencessc.consent.manager.process.init
  (:require [org.healthsciencessc.consent.manager.process.consent-history]
            [org.healthsciencessc.consent.manager.process.designer]
            [org.healthsciencessc.consent.manager.process.endorsement]
            [org.healthsciencessc.consent.manager.process.endorsement-type]
            [org.healthsciencessc.consent.manager.process.groups]
            [org.healthsciencessc.consent.manager.process.language]
            [org.healthsciencessc.consent.manager.process.locations]
            [org.healthsciencessc.consent.manager.process.login]
            [org.healthsciencessc.consent.manager.process.meta-item]
            [org.healthsciencessc.consent.manager.process.organizations]
            [org.healthsciencessc.consent.manager.process.policy]
            [org.healthsciencessc.consent.manager.process.policy-definition]
            [org.healthsciencessc.consent.manager.process.protocol]
            [org.healthsciencessc.consent.manager.process.protocol-version]
            [org.healthsciencessc.consent.manager.process.roles]
            [org.healthsciencessc.consent.manager.process.role-mapping]
            [org.healthsciencessc.consent.manager.process.text-i18n]
            [org.healthsciencessc.consent.manager.process.users]))