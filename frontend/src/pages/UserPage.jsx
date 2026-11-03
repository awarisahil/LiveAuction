import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';
import AuctionCard from '../components/AuctionCard';

export default function UserPage() {
  const { id } = useParams();
  const [profile, setProfile] = useState(null);
  const [listings, setListings] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api.user(id).then(setProfile).catch((e) => setError(e.message));
    api.auctions().then((d) => setListings(d.auctions.filter((a) => a.sellerId === id)));
  }, [id]);

  if (error) return <main className="detail"><p className="error">{error}</p></main>;
  if (!profile) return <main className="detail"><p className="muted">Loading…</p></main>;

  return (
    <main className="detail">
      <div className="profile-card">
        <div className="avatar-lg">{profile.avatarEmoji || '\uD83D\uDC64'}</div>
        <div>
          <h2>{profile.name}</h2>
          <p className="muted">{profile.role} · member since {new Date(profile.createdAt).toLocaleDateString()}</p>
          {profile.bio && <p>{profile.bio}</p>}
          <p className="muted">
            {profile.bidsPlaced} bids placed · {profile.auctionsWon} auctions won · {profile.listings} listings
          </p>
        </div>
      </div>
      <h3>Active listings</h3>
      <div className="grid">
        {listings.map((a) => <AuctionCard key={a.id} auction={a} />)}
        {listings.length === 0 && <p className="muted">No live listings right now.</p>}
      </div>
    </main>
  );
}
