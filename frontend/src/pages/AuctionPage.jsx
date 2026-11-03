import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, newIdemKey } from '../api';
import { useAuth } from '../auth';
import { subscribe } from '../ws';
import { useToast } from '../toast';
import Countdown from '../components/Countdown';
import BidForm from '../components/BidForm';
import PayButton from '../components/PayButton';
import WatchButton from '../components/WatchButton';
import { fmt } from '../components/AuctionCard';

export default function AuctionPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const notify = useToast();
  const [auction, setAuction] = useState(null);
  const [bids, setBids] = useState([]);
  const [offset, setOffset] = useState(0);
  const [loadError, setLoadError] = useState('');
  const [bidError, setBidError] = useState('');
  const [pending, setPending] = useState(false);
  const [watched, setWatched] = useState(false);
  const idemKey = useRef(newIdemKey());
  const prevLeader = useRef(null);

  useEffect(() => {
    let alive = true;
    const unsubscribers = [];

    api.auction(id)
      .then((d) => {
        if (!alive) return;
        setAuction(d.auction);
        setBids(d.recentBids);
        setOffset(d.serverNow - Date.now());
      })
      .catch((e) => alive && setLoadError(e.message));

    subscribe(`/topic/auction/${id}/bids`, (bid) => {
      setBids((prev) => [bid, ...prev.filter((b) => b.id !== bid.id)].slice(0, 50));
    })
      .then((u) => unsubscribers.push(u))
      .catch(() => {});

    subscribe(`/topic/auction/${id}/events`, (event) => {
      if (event.type === 'BID_ACCEPTED' && event.auction) {
        const prev = prevLeader.current;
        const next = event.auction.leader;
        setAuction(event.auction);
        if (user && prev && prev === user.id && next && next !== user.id) {
          notify(`You were outbid on "${event.auction.title}"`, 'warn');
        }
      } else if (event.type === 'AUCTION_ENDED' && event.auction) {
        setAuction(event.auction);
        if (user && event.auction.leader === user.id) {
          notify(`You won "${event.auction.title}" — settle it from your profile.`, 'success');
        } else {
          notify(`"${event.auction.title}" has ended.`, 'info');
        }
      } else if (event.type === 'AUCTION_CANCELLED' && event.auction) {
        setAuction(event.auction);
        notify(`"${event.auction.title}" was cancelled.`, 'error');
      } else if (event.type === 'PAYMENT_COMPLETED' && event.receipt) {
        setAuction((a) => a && { ...a, paid: true });
        if (user && event.receipt.payer === user.id) {
          notify(`Payment confirmed (receipt ${event.receipt.id})`, 'success');
        }
      }
    })
      .then((u) => unsubscribers.push(u))
      .catch(() => {});

    return () => {
      alive = false;
      unsubscribers.forEach((u) => u());
    };
  }, [id]);

  useEffect(() => {
    prevLeader.current = auction?.leader ?? null;
  }, [auction?.leader]);

  useEffect(() => {
    if (!user) return;
    api.watchlist()
      .then((list) => setWatched(list.some((a) => a.id === id)))
      .catch(() => {});
  }, [user, id]);

  const minBidCents = auction ? auction.currentBidCents + auction.minIncrementCents : 0;
  const live = auction?.status === 'ACTIVE';
  const mine = user && auction && auction.leader === user.id;
  const ownListing = user && auction && auction.sellerId === user.id;

  const submitBid = async (amountCents) => {
    setBidError('');
    setPending(true);
    try {
      const d = await api.placeBid(id, amountCents, idemKey.current);
      setAuction(d.auction);
      idemKey.current = newIdemKey();
      notify('Bid placed', 'success');
    } catch (e) {
      if (e.code === 'BID_TOO_LOW' && e.minNextCents) {
        setBidError(`Minimum next bid is ${fmt(e.minNextCents)}.`);
      } else if (e.status === 401) {
        setBidError('Please log in to bid.');
      } else {
        setBidError(e.message);
      }
    } finally {
      setPending(false);
    }
  };

  if (loadError && !auction) {
    return <main className="detail"><p className="error">{loadError}</p></main>;
  }
  if (!auction) {
    return <main className="detail"><p className="muted">Loading…</p></main>;
  }

  return (
    <main className="detail">
      <div className="detail-head">
        {auction.imageUrl ? (
          <img className="detail-img" src={auction.imageUrl} alt={auction.title} />
        ) : (
          <div className="card-emoji large">{auction.imageEmoji}</div>
        )}
        <div className="detail-info">
          <div className="detail-top">
            <span className="cat-badge">{auction.category}</span>
            {user && !ownListing && (
              <WatchButton auctionId={auction.id} initial={watched} onChange={setWatched} />
            )}
          </div>
          <h2>{auction.title}</h2>
          <p className="muted">{auction.description}</p>
          <p className="price big">{fmt(auction.currentBidCents)}</p>
          <p className="muted">
            {auction.bidCount} bids
            {auction.leaderName
              ? ` · leading: ${auction.leaderName}${mine ? ' (you)' : ''}`
              : ' · no bids yet'}
          </p>
          <p className="muted">
            Seller: <Link to={`/user/${auction.sellerId}`} className="seller-link">{auction.sellerName}</Link>
          </p>
          <Countdown endsAt={auction.endsAt} offset={offset} ended={!live} />
          {live && <p className="muted small">Late bids extend the deadline by 30s (anti-sniping).</p>}
        </div>
      </div>

      {live && !ownListing && (
        user ? (
          <BidForm minBidCents={minBidCents} onBid={submitBid} pending={pending} error={bidError} />
        ) : (
          <p className="muted"><Link to="/login">Log in</Link> to place a bid.</p>
        )
      )}
      {live && ownListing && <p className="muted">This is your listing — you cannot bid on it.</p>}

      {!live && mine && !auction.paid && (
        <PayButton auctionId={auction.id} onPaid={() => setAuction((a) => ({ ...a, paid: true }))} />
      )}
      {!live && mine && auction.paid && <p className="badge badge-live">PAID</p>}
      {!live && !mine && <p className="badge badge-ended">{auction.status === 'CANCELLED' ? 'CANCELLED' : auction.paid ? 'SOLD & PAID' : 'ENDED'}</p>}

      <h3>Recent bids</h3>
      <ul className="bids">
        {bids.map((b) => (
          <li key={b.id} className={user && b.bidderId === user.id ? 'mine' : ''}>
            <span>{b.bidder}{user && b.bidderId === user.id ? ' (you)' : ''}</span>
            <span>{fmt(b.amountCents)}</span>
            <span className="muted">{new Date(b.at).toLocaleTimeString()}</span>
          </li>
        ))}
        {bids.length === 0 && <li className="muted">No bids yet — be the first!</li>}
      </ul>
    </main>
  );
}
