package me.demro.dBans.api.adapter;

import me.demro.dBans.model.Punishment;
import me.demro.dlibs.api.PunishmentType;

import java.util.UUID;

public class PunishmentAdapter implements me.demro.dlibs.api.Punishment {
    private final Punishment delegate;

    public PunishmentAdapter(Punishment delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getId() {
        return delegate != null ? delegate.getId() : null;
    }

    @Override
    public UUID getPlayerUuid() {
        return delegate != null ? delegate.getPlayerUuid() : null;
    }

    @Override
    public String getPlayerName() {
        return delegate != null ? delegate.getPlayerName() : null;
    }

    @Override
    public UUID getIssuerUuid() {
        return delegate != null ? delegate.getIssuerUuid() : null;
    }

    @Override
    public String getIssuerName() {
        return delegate != null ? delegate.getIssuerName() : null;
    }

    @Override
    public PunishmentType getType() {
        if (delegate == null || delegate.getType() == null) {
            return null;
        }
        return PunishmentType.valueOf(delegate.getType().name());
    }

    @Override
    public String getReason() {
        return delegate != null ? delegate.getReason() : null;
    }

    @Override
    public long getStartTime() {
        return delegate != null ? delegate.getStartTime() : 0;
    }

    @Override
    public Long getEndTime() {
        return delegate != null ? delegate.getEndTime() : null;
    }

    @Override
    public boolean isActive() {
        return delegate != null && delegate.isActive();
    }

    @Override
    public boolean isExpired() {
        return delegate != null && delegate.isExpired();
    }

    @Override
    public String getServerName() {
        return delegate != null ? delegate.getServerName() : null;
    }
}