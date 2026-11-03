import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { useToast } from '../toast';
import { fmt } from '../components/AuctionCard';

const TABS = ['bids', 'winnings', 'listings', 'watchlist', 'payments'];
const DEPOSITS = [100_000, 1_000_000, 5_000_000];

export default function ProfilePage() {
  const { user, refresh } = useAuth();
  const notify = useToast();
  const [tab, setTab] = useState('bids');
  const [bids, setBids] = useState([]);
  const [winnings, setWinnings] = useState([]);
  const [listings, setListings] = useState([]);
  const [watchlist, setWatchlist] = useState([]);
  const [payments, setPayments] = useState([]);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ name: '', avatarEmoji: '', bio: '' });

  const load = useCallback(() => {
    refresh().catch(() => {});
    if (tab === 'bids') api.myBids().then(setBids).catch(() => {});
    if (tab === 'winnings') api.winnings().then(setWinnings).catch(() => {});
    if (tab === 'listings') api.listings().then(setListings).catch(() => {});
    if (tab === 'watchlist') api.watchlist().then(setWatchlist).catch(() => {});
    if (tab === 'payments') api.payments().then(setPayments).catch(() => {});
  }, [tab, refresh]);

  useEffect(load, [load]);

  useEffect(() => {
    if (user) setForm({ name: user.name || '', avatarEmoji: user.avatarEmoji || '', bio: user.bio || '' });
  }, [user?.id]);

  const deposit = async (amountCents) => {
    try {
      await api.deposit(amountCents);
      await refresh();
      notify(`${fmt(amountCents)} added to your wallet`, 'success');
    } catch (e) {
      notify(e.message, 'error');
    }
  };

  const saveProfile = async (e) => {
    e.preventDefault();
    try {
      await api.updateMe(form);
      await refresh();
      setEditing(false);
      notify('Profile updated', 'success');
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  if (!user) return null;

  return (
    <main className="detail">
      <div className="profile-card">
        <div className="avatar-lg">{user.avatarEmoji || '\uD83D\uDC64'}</div>
        <div className="profile-head">
          {editing ? (
            <form className="inline-edit" onSubmit={saveProfile}>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} maxLength={40} />
              <input value={form.avatarEmoji} onChange={(e) => setForm((f) => ({ ...f, avatarEmoji: e.target.value }))} maxLength={8} placeholder="Avatar emoji" />
              <textarea value={form.bio} onChange={(e) => setForm((f) => ({ ...f, bio: e.target.value }))} rows={2} maxLength={300} placeholder="Tell other members about yourself..." />
              <div>
                <button type="submit">Save</button>{' '}
                <button type="button" className="ghost" onClick={() => setEditing(false)}>Cancel</button>
              </div>
            </form>
          ) : (
            <>
              <h2>{user.name}</h2>
              <p className="muted">{user.email} · {user.role}</p>
              {user.bio && <p>{user.bio}</p>}
              <button className="ghost small" onClick={() => setEditing(true)}>Edit profile</button>
            </>
          )}
        </div>
        <div className="wallet-card">
          <p className="muted">Wallet balance</p>
          <p className="price big">{fmt(user.balanceCents ?? 0)}</p>
          <div className="deposit-row">
            {DEPOSITS.map((c) => (
              <button key={c} className="ghost small" onClick={() => deposit(c)}>
                +{fmt(c)}
              </button>
            ))}
          </div>
          <p className="muted small">Demo card processor — instant top-ups.</p>
        </div>
      </div>

      <div className="tabs">
        {TABS.map((t) => (
          <button key={t} className={`chip ${tab === t ? 'chip-active' : ''}`} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'bids' && (
        <ul className="bids">
          {bids.map((b) => (
            <li key={b.id}>
              <span><Link to={`/auction/${b.auctionId}`} className="seller-link">{b.auctionTitle}</Link></span>
              <span>{fmt(b.amountCents)}</span>
              <span className="muted">{new Date(b.at).toLocaleString()}</span>
            </li>
          ))}
          {bids.length === 0 && <li className="muted">You haven't placed any bids yet.</li>}
        </ul>
      )}

      {tab === 'winnings' && (
        <ul className="bids">
          {winnings.map((a) => (
            <li key={a.id}>
              <span><Link to={`/auction/${a.id}`} className="seller-link">{a.title}</Link></span>
              <span>{fmt(a.currentBidCents)}</span>
              <span className={a.paid ? 'badge badge-live' : 'badge badge-ended'}>{a.paid ? 'PAID' : 'AWAITING PAYMENT'}</span>
            </li>
          ))}
          {winnings.length === 0 && <li className="muted">No winnings yet — go win something!</li>}
        </ul>
      )}

      {tab === 'listings' && (
        <ul className="bids">
          {listings.map((a) => (
            <li key={a.id}>
              <span><Link to={`/auction/${a.id}`} className="seller-link">{a.title}</Link></span>
              <span>{fmt(a.currentBidCents)}</span>
              <span className={`badge ${a.status === 'ACTIVE' ? 'badge-live' : 'badge-ended'}`}>
                {a.status}{a.paid ? ' · PAID' : ''}
              </span>
            </li>
          ))}
          {listings.length === 0 && <li className="muted">You have no listings. <Link to="/sell">Sell something!</Link></li>}
        </ul>
      )}

      {tab === 'watchlist' && (
        <ul className="bids">
          {watchlist.map((a) => (
            <li key={a.id}>
              <span><Link to={`/auction/${a.id}`} className="seller-link">{a.title}</Link></span>
              <span>{fmt(a.currentBidCents)}</span>
              <span className={`badge ${a.status === 'ACTIVE' ? 'badge-live' : 'badge-ended'}`}>{a.status}</span>
            </li>
          ))}
          {watchlist.length === 0 && <li className="muted">Your watchlist is empty — tap the star on any auction.</li>}
        </ul>
      )}

      {tab === 'payments' && (
        <ul className="bids">
          {payments.map((p) => (
            <li key={p.id}>
              <span>Receipt {p.id}</span>
              <span>{fmt(p.amountCents)} (fee {fmt(p.feeCents)})</span>
              <span className="muted">{new Date(p.paidAt).toLocaleString()}</span>
            </li>
          ))}
          {payments.length === 0 && <li className="muted">No payments yet.</li>}
        </ul>
      )}
    </main>
  );
}
