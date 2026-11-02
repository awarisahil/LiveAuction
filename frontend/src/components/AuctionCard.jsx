export default function AuctionCard({ auction, onOpen }) {
  const live = auction.status === 'ACTIVE';
  return (
    <div className={`card ${live ? '' : 'card-ended'}`} onClick={onOpen}>
      <div className="card-emoji">{auction.imageEmoji}</div>
      <div className="card-body">
        <h3>{auction.title}</h3>
        <p className="muted">{auction.bidCount} bids</p>
        <p className="price">${(auction.currentBidCents / 100).toFixed(2)}</p>
        <span className={`badge ${live ? 'badge-live' : 'badge-ended'}`}>
          {live ? 'LIVE' : auction.paid ? 'SOLD & PAID' : 'ENDED'}
        </span>
      </div>
    </div>
  );
}
