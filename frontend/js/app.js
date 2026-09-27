/**
 * SMART TRANSIT MANAGEMENT SYSTEM - FRONTEND APPLICATION JAVASCRIPT
 * Handles API communication, session management, UI rendering, and reactive updates.
 */

const API_BASE = 'http://localhost:8080/api';

// -------------------------------------------------------------------
// 1. SESSION MANAGEMENT
// -------------------------------------------------------------------
function getCurrentUser() {
    const raw = sessionStorage.getItem('transit_user');
    return raw ? JSON.parse(raw) : null;
}

function getCurrentAdmin() {
    const raw = sessionStorage.getItem('transit_admin');
    return raw ? JSON.parse(raw) : null;
}

function setCurrentUser(user) {
    sessionStorage.setItem('transit_user', JSON.stringify(user));
    sessionStorage.removeItem('transit_admin');
}

function setCurrentAdmin(admin) {
    sessionStorage.setItem('transit_admin', JSON.stringify(admin));
    sessionStorage.removeItem('transit_user');
}

function logout() {
    sessionStorage.clear();
    window.location.href = 'login.html';
}

// -------------------------------------------------------------------
// 2. HTTP CLIENT HELPER
// -------------------------------------------------------------------
async function api(endpoint, method = 'GET', data = null) {
    const options = {
        method: method,
        headers: {
            'Content-Type': 'application/json'
        }
    };
    if (data && (method === 'POST' || method === 'PUT')) {
        options.body = JSON.stringify(data);
    }

    try {
        const res = await fetch(`${API_BASE}${endpoint}`, options);
        const json = await res.json();
        if (!res.ok) {
            throw new Error(json.error || `HTTP ${res.status}: Request failed`);
        }
        return json;
    } catch (err) {
        console.error("API Error:", err);
        throw err;
    }
}

// -------------------------------------------------------------------
// 3. UI NOTIFICATIONS & TOASTS
// -------------------------------------------------------------------
function showAlert(containerId, message, type = 'info') {
    const c = document.getElementById(containerId);
    if (!c) return;
    c.innerHTML = `
        <div class="alert alert-${type}">
            <span>${message}</span>
        </div>
    `;
    setTimeout(() => {
        if (c.innerHTML.includes(message)) {
            c.innerHTML = '';
        }
    }, 6000);
}

// -------------------------------------------------------------------
// 4. DYNAMIC NAVIGATION BAR
// -------------------------------------------------------------------
function setupNavbar(activePage = '') {
    const navRight = document.getElementById('nav-right');
    if (!navRight) return;

    const user = getCurrentUser();
    const admin = getCurrentAdmin();

    if (admin) {
        navRight.innerHTML = `
            <li><a href="admin.html" class="${activePage === 'admin' ? 'active' : ''}">Admin Portal</a></li>
            <li><a href="search.html" class="${activePage === 'search' ? 'active' : ''}">Search Trains</a></li>
            <li><span style="color:#fbbf24; font-weight:600; font-size:0.9rem;">Admin: ${admin.username}</span></li>
            <li><button onclick="logout()" class="btn btn-outline btn-sm" style="color:#fff; border-color:#94a3b8;">Logout</button></li>
        `;
    } else if (user) {
        navRight.innerHTML = `
            <li><a href="dashboard.html" class="${activePage === 'dashboard' ? 'active' : ''}">Dashboard</a></li>
            <li><a href="search.html" class="${activePage === 'search' ? 'active' : ''}">Search</a></li>
            <li><a href="bookings.html" class="${activePage === 'bookings' ? 'active' : ''}">My Bookings</a></li>
            <li><a href="notifications.html" class="${activePage === 'notifications' ? 'active' : ''}">
                Alerts <span id="nav-notif-badge" class="nav-badge" style="display:none;">0</span>
            </a></li>
            <li><span style="color:#38bdf8; font-weight:600; font-size:0.9rem;">${user.fullName} (${user.category})</span></li>
            <li><button onclick="logout()" class="btn btn-outline btn-sm" style="color:#fff; border-color:#94a3b8;">Logout</button></li>
        `;
        fetchUnreadNotificationCount(user.userId);
    } else {
        navRight.innerHTML = `
            <li><a href="index.html" class="${activePage === 'home' ? 'active' : ''}">Home</a></li>
            <li><a href="search.html" class="${activePage === 'search' ? 'active' : ''}">Search Trains</a></li>
            <li><a href="login.html" class="btn btn-outline btn-sm" style="color:#fff; border-color:#94a3b8;">Login</a></li>
            <li><a href="register.html" class="btn btn-primary btn-sm">Register</a></li>
        `;
    }
}

async function fetchUnreadNotificationCount(userId) {
    try {
        const res = await api(`/notifications?userId=${userId}`);
        if (res.success && res.notifications) {
            const unread = res.notifications.filter(n => !n.isRead).length;
            const badge = document.getElementById('nav-notif-badge');
            if (badge) {
                if (unread > 0) {
                    badge.textContent = unread;
                    badge.style.display = 'inline-block';
                } else {
                    badge.style.display = 'none';
                }
            }
        }
    } catch (ignored) {}
}

// -------------------------------------------------------------------
// 5. TRAIN SEARCH & SMART RECOMMENDATION LOGIC
// -------------------------------------------------------------------
async function executeTrainSearch(source, destination, date) {
    const resultsContainer = document.getElementById('search-results');
    const altContainer = document.getElementById('smart-alternatives');
    if (!resultsContainer) return;

    resultsContainer.innerHTML = '<div style="padding:2rem; text-align:center;">Searching trains...</div>';
    if (altContainer) altContainer.innerHTML = '';

    try {
        const query = `/trains/search?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}&date=${encodeURIComponent(date || '')}`;
        const res = await api(query);

        if (!res.trains || res.trains.length === 0) {
            resultsContainer.innerHTML = `
                <div class="card" style="text-align:center; padding:2.5rem;">
                    <h3>No direct trains found</h3>
                    <p style="color:var(--text-muted); margin-top:0.5rem;">Try adjusting stations or searching without a specific journey date.</p>
                </div>
            `;
            return;
        }

        let html = '';
        let fullTrains = [];

        res.trains.forEach(t => {
            const isFull = (t.avail1a + t.avail2a + t.avail3a + t.availSl) === 0;
            if (isFull || t.status === 'CANCELLED') {
                fullTrains.push(t);
            }

            const statusClass = t.status === 'CANCELLED' ? 'badge-cancelled' : (t.status === 'DELAYED' ? 'badge-delayed' : 'badge-ontime');

            html += `
            <div class="card" style="margin-bottom:1.25rem;">
                <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:1rem;">
                    <div>
                        <div style="display:flex; align-items:center; gap:0.75rem;">
                            <h3 style="font-size:1.25rem; color:var(--primary-navy);">${t.trainNumber} - ${t.trainName}</h3>
                            <span class="badge ${statusClass}">${t.status}</span>
                        </div>
                        <p style="color:var(--text-muted); font-size:0.9rem; margin-top:0.25rem;">
                            Date: <strong>${t.journeyDate}</strong> | ${t.delayInfo || ''}
                        </p>
                    </div>
                </div>

                <div class="journey-timeline" style="margin:1rem 0;">
                    <div class="timeline-station">
                        <div class="time">${t.departureTime}</div>
                        <div class="city">${t.source}</div>
                    </div>
                    <div class="timeline-arrow">
                        <span class="duration">Direct Route</span>
                        <div class="line"></div>
                    </div>
                    <div class="timeline-station">
                        <div class="time">${t.arrivalTime}</div>
                        <div class="city">${t.destination}</div>
                    </div>
                </div>

                <div style="font-weight:600; font-size:0.9rem; margin-bottom:0.5rem;">Class-wise Seat Availability & Fares:</div>
                <div class="availability-grid">
                    ${renderAvailChip(t.trainId, '1A', 'First AC', t.avail1a, t.fare1a, t.status)}
                    ${renderAvailChip(t.trainId, '2A', 'Second AC', t.avail2a, t.fare2a, t.status)}
                    ${renderAvailChip(t.trainId, '3A', 'Third AC', t.avail3a, t.fare3a, t.status)}
                    ${renderAvailChip(t.trainId, 'SL', 'Sleeper', t.availSl, t.fareSl, t.status)}
                </div>
            </div>
            `;
        });

        resultsContainer.innerHTML = html;

        // SMART RECOMMENDATION: If any train is full or cancelled, fetch smart alternatives
        if (fullTrains.length > 0 && altContainer) {
            loadSmartAlternatives(fullTrains[0], altContainer);
        }

    } catch (err) {
        resultsContainer.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
    }
}

function renderAvailChip(trainId, clsCode, clsName, availCount, fare, trainStatus) {
    const isAvailable = availCount > 0 && trainStatus !== 'CANCELLED';
    const countClass = isAvailable ? 'open' : (trainStatus === 'CANCELLED' ? 'full' : 'wl');
    const countLabel = trainStatus === 'CANCELLED' ? 'CANCELLED' : (availCount > 0 ? `${availCount} Available` : 'WL Available');

    return `
        <div class="avail-card">
            <div class="avail-class">${clsCode} - ${clsName}</div>
            <div class="avail-count ${countClass}">${countLabel}</div>
            <div class="avail-fare">₹${fare.toFixed(2)}</div>
            <button onclick="startBooking(${trainId}, '${clsCode}')" 
                    class="btn btn-primary btn-sm" 
                    style="margin-top:0.4rem; width:100%;"
                    ${trainStatus === 'CANCELLED' ? 'disabled' : ''}>
                Book
            </button>
        </div>
    `;
}

async function loadSmartAlternatives(fullTrain, container) {
    try {
        const res = await api(`/trains/alternatives?source=${encodeURIComponent(fullTrain.source)}&destination=${encodeURIComponent(fullTrain.destination)}&date=${encodeURIComponent(fullTrain.journeyDate)}&excludeId=${fullTrain.trainId}`);
        if (res.success && res.alternatives && res.alternatives.length > 0) {
            let altHtml = `
                <div class="card alt-train-card">
                    <div style="display:flex; align-items:center; gap:0.5rem; margin-bottom:0.75rem;">
                        <span style="font-size:1.3rem;">💡</span>
                        <div>
                            <strong style="color:var(--accent-orange); font-size:1.05rem;">Smart Alternative Train Recommendations</strong>
                            <p style="font-size:0.85rem; color:var(--text-muted);">
                                Train #${fullTrain.trainNumber} has limited or exhausted seats. Consider these alternatives on the same route with available seats:
                            </p>
                        </div>
                    </div>
                    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap:1rem;">
            `;

            res.alternatives.forEach(alt => {
                altHtml += `
                    <div style="background:#fff; border:1px solid #fed7aa; border-radius:4px; padding:0.85rem;">
                        <div style="display:flex; justify-content:space-between;">
                            <strong>Train ${alt.trainNumber}</strong>
                            <span style="font-size:0.8rem; color:#16a34a; font-weight:700;">Seats Available</span>
                        </div>
                        <div style="font-size:0.9rem; color:var(--text-main); margin:0.25rem 0;">${alt.trainName}</div>
                        <div style="font-size:0.85rem; color:var(--text-muted);">
                            ${alt.source} (${alt.departureTime}) → ${alt.destination} (${alt.arrivalTime})
                        </div>
                        <div style="font-size:0.85rem; margin-top:0.4rem; display:flex; justify-content:space-between; align-items:center;">
                            <span>SL Fare: <strong>₹${alt.fareSl}</strong></span>
                            <button onclick="startBooking(${alt.trainId}, 'SL')" class="btn btn-primary btn-sm">Select</button>
                        </div>
                    </div>
                `;
            });

            altHtml += `</div></div>`;
            container.innerHTML = altHtml;
        }
    } catch (ignored) {}
}

function startBooking(trainId, travelClass) {
    const user = getCurrentUser();
    if (!user) {
        alert("Please login as a registered user to book tickets.");
        window.location.href = `login.html?redirect=booking.html?trainId=${trainId}&class=${travelClass}`;
        return;
    }
    window.location.href = `booking.html?trainId=${trainId}&class=${travelClass}`;
}
