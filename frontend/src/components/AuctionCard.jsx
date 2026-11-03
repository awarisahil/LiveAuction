import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { useToast } from '../toast';
import WatchButton from './WatchButton';

export const fmt = (cents) => `$${(cents / 100).toFixed(2)}`;

export default function AuctionCard({ auction: a, watched = false }) {
  const { user } = useAuth();
  const notify = useToast();
  const live = a.status === 'ACTIVE';

  const onWatchChange = (isWatched) => {
    notify(isWatched ? `Watching "${a.title}"` : `Removed "${a.title}" from watchlist`, 'info');
  };

  return (
    <div className={`card ${live ? '' : 'card-ended'}`}>
      <Link to={`/auction/${a.id}`} className="card-main">
        {a.imageUrl ? (
          <img className="card-img" src={a.imageUrl} alt={a.title} />
        ) : (
          <div className="card-emoji">{a.imageEmoji}</div>
        )}
        <div className="card-body">
          <span className="cat-badge">{a.category}</span>
          <h3>{a.title}</h3>
          <p className="muted">
            {a.bidCount} bids · seller{' '}
            <Link to={`/user/${a.sellerId}`} className="seller-link" onClick={(e) => e.stopPropagation()}>
              {a.sellerName}
            </Link>
          </p>
          <p className="price">{fmt(a.currentBidCents)}</p>
          <span className={`badge ${live ? 'badge-live' : 'badge-ended'}`}>
            {live ? 'LIVE' : a.paid ? 'SOLD & PAID' : a.status === 'CANCELLED' ? 'CANCELLED' : 'ENDED'}
          </span>
        </div>
      </Link>
      {user && (
        <div className="card-watch">
          <WatchButton auctionId={a.id} initial={watched} onChange={onWatchChange} />
        </div>
      )}
    </div>
  );
}
