import { useEffect, useRef, useState } from 'react';
import { getAuction, newIdemKey, placeBid } from '../api';
import { subscribe } from '../ws';
import { useToast } from '../toast';
import Countdown from './Countdown';
import BidForm from './BidForm';
import PayButton from './PayButton';

export default function AuctionDetail({ id, bidder, onBack }) {
  const notify = useToast();
  const [auction, setAuction] = useState(null);
  const [bids, setBids] = useState([]);
  const [offset, setOffset] = useState(0);
  const [loadError, setLoadError] = useState('');
  const [bidError, setBidError] = useState('');
  const [pending, setPending] = useState(false);
  const idemKey = useRef(newIdemKey());
  const prevLeader = useRef(null);

  useEffect(() => {
    let alive = true;
    const unsubscribers = [];

    getAuction(id)
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
        if (prev && prev === bidder && next && next !== bidder) {
          notify(`You were outbid on "${event.auction.title}"`, 'warn');
        }
      } else if (event.type === 'AUCTION_ENDED' && event.auction) {
        setAuction(event.auction);
        if (event.auction.leader === bidder) {
          notify(`You won "${event.auction.title}" — time to pay!`, 'success');
        } else {
          notify(`"${event.auction.title}" has ended.`, 'info');
        }
      } else if (event.type === 'PAYMENT_COMPLETED' && event.receipt) {
        setAuction((a) => a && { ...a, paid: true });
        if (event.receipt.payer === bidder) {
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

  const minBidCents = auction ? auction.currentBidCents + auction.minIncrementCents : 0;

  const submitBid = async (amountCents) => {
    setBidError('');
    setPending(true);
    try {
      const d = await placeBid(id, bidder, amountCents, idemKey.current);
      setAuction(d.auction);
      idemKey.current = crypto.randomUUID(); // success: subsequent bids are new operations
    } catch (e) {
      if (e.code === 'BID_TOO_LOW' && e.minNextCents) {
        setBidError(`Minimum next bid is $${(e.minNextCents / 100).toFixed(2)}.`);
      } else {
        setBidError(e.message);
      }
      // Same idempotency key is kept: failed attempts never consume the key,
      // so a retry of this operation is still deduplicated server-side.
    } finally {
      setPending(false);
    }
  };

  if (loadError && !auction) {
    return (
      <main className="detail">
        <button className="link" onClick={onBack}>← All auctions</button>
        <p className="error">{loadError}</p>
      </main>
    );
  }
  if (!auction) {
    return (
      <main className="detail">
        <button className="link" onClick={onBack}>← All auctions</button>
        <p className="muted">Loading…</p>
      </main>
    );
  }

  const live = auction.status === 'ACTIVE';
  const mine = auction.leader === bidder;

  return (
    <main className="detail">
      <button className="link" onClick={onBack}>← All auctions</button>
      <div className="detail-head">
        <div className="card-emoji large">{auction.imageEmoji}</div>
        <div>
          <h2>{auction.title}</h2>
          <p className="muted">{auction.description}</p>
          <p className="price big">${(auction.currentBidCents / 100).toFixed(2)}</p>
          <p className="muted">
            {auction.bidCount} bids
            {auction.leader ? ` · leading: ${auction.leader}${mine ? ' (you)' : ''}` : ''}
          </p>
          <Countdown endsAt={auction.endsAt} offset={offset} ended={!live} />
        </div>
      </div>

      {live && (
        <BidForm minBidCents={minBidCents} onBid={submitBid} pending={pending} error={bidError} />
      )}
      {!live && mine && !auction.paid && (
        <PayButton auctionId={id} bidder={bidder} onPaid={() => setAuction((a) => ({ ...a, paid: true }))} />
      )}
      {!live && auction.paid && <p className="badge badge-ended">SOLD &amp; PAID</p>}

      <h3>Recent bids</h3>
      <ul className="bids">
        {bids.map((b) => (
          <li key={b.id} className={b.bidder === bidder ? 'mine' : ''}>
            <span>{b.bidder}{b.bidder === bidder ? ' (you)' : ''}</span>
            <span>${(b.amountCents / 100).toFixed(2)}</span>
            <span className="muted">{new Date(b.at).toLocaleTimeString()}</span>
          </li>
        ))}
        {bids.length === 0 && <li className="muted">No bids yet — be the first!</li>}
      </ul>
    </main>
  );
}
