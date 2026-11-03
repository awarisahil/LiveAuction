import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useToast } from '../toast';
import { fmt } from '../components/AuctionCard';

const TABS = ['stats', 'users', 'auctions'];

export default function AdminPage() {
  const notify = useToast();
  const [tab, setTab] = useState('stats');
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [auctions, setAuctions] = useState([]);

  const load = useCallback(() => {
    if (tab === 'stats') api.adminStats().then(setStats).catch((e) => notify(e.message, 'error'));
    if (tab === 'users') api.adminUsers().then(setUsers).catch((e) => notify(e.message, 'error'));
    if (tab === 'auctions') api.adminAuctions().then(setAuctions).catch((e) => notify(e.message, 'error'));
  }, [tab, notify]);

  useEffect(load, [load]);

  const updateUser = async (id, payload, successMsg) => {
    try {
      await api.adminUpdateUser(id, payload);
      notify(successMsg, 'success');
      load();
    } catch (e) {
      notify(e.message, 'error');
    }
  };

  const cancelAuction = async (id) => {
    try {
      await api.adminCancel(id);
      notify('Auction cancelled', 'success');
      load();
    } catch (e) {
      notify(e.message, 'error');
    }
  };

  return (
    <main className="detail">
      <h2>Admin console</h2>
      <div className="tabs">
        {TABS.map((t) => (
          <button key={t} className={`chip ${tab === t ? 'chip-active' : ''}`} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'stats' && stats && (
        <div className="stats-grid">
          <div className="stat-card"><p className="muted">Registered users</p><p className="stat-num">{stats.users}</p></div>
          <div className="stat-card"><p className="muted">Live auctions</p><p className="stat-num">{stats.activeAuctions}</p></div>
          <div className="stat-card"><p className="muted">Finished auctions</p><p className="stat-num">{stats.endedAuctions}</p></div>
          <div className="stat-card"><p className="muted">Total bids</p><p className="stat-num">{stats.totalBids}</p></div>
          <div className="stat-card"><p className="muted">Gross merchandise value</p><p className="stat-num">{fmt(stats.grossMerchandiseValueCents)}</p></div>
          <div className="stat-card"><p className="muted">Platform revenue (5%)</p><p className="stat-num">{fmt(stats.platformRevenueCents)}</p></div>
        </div>
      )}

      {tab === 'users' && (
        <table className="admin-table">
          <thead>
            <tr><th></th><th>Name</th><th>Email</th><th>Role</th><th>Balance</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.avatarEmoji}</td>
                <td><Link className="seller-link" to={`/user/${u.id}`}>{u.name}</Link></td>
                <td className="muted">{u.email}</td>
                <td>{u.role}</td>
                <td>{fmt(u.balanceCents)}</td>
                <td>{u.banned ? <span className="badge badge-ended">BANNED</span> : <span className="badge badge-live">ACTIVE</span>}</td>
                <td className="actions">
                  <button className="ghost small" onClick={() => updateUser(u.id, { banned: !u.banned }, u.banned ? 'User unbanned' : 'User banned')}>
                    {u.banned ? 'Unban' : 'Ban'}
                  </button>
                  {u.role === 'BUYER' && (
                    <button className="ghost small" onClick={() => updateUser(u.id, { role: 'SELLER' }, 'Promoted to seller')}>Make seller</button>
                  )}
                  {u.role === 'SELLER' && (
                    <button className="ghost small" onClick={() => updateUser(u.id, { role: 'BUYER' }, 'Demoted to buyer')}>Make buyer</button>
                  )}
                  {u.role !== 'ADMIN' && (
                    <button className="ghost small" onClick={() => updateUser(u.id, { role: 'ADMIN' }, 'Promoted to admin')}>Make admin</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {tab === 'auctions' && (
        <table className="admin-table">
          <thead>
            <tr><th>Title</th><th>Seller</th><th>Status</th><th>Current bid</th><th>Bids</th><th>Action</th></tr>
          </thead>
          <tbody>
            {auctions.map((a) => (
              <tr key={a.id}>
                <td><Link className="seller-link" to={`/auction/${a.id}`}>{a.title}</Link></td>
                <td>{a.sellerName}</td>
                <td>{a.status}{a.paid ? ' · PAID' : ''}</td>
                <td>{fmt(a.currentBidCents)}</td>
                <td>{a.bidCount}</td>
                <td>
                  {a.status === 'ACTIVE' && (
                    <button className="ghost small" onClick={() => cancelAuction(a.id)}>Cancel</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
