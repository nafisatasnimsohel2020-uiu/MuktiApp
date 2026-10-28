/**
 * MUKTI APP - GLOBAL JAVASCRIPT ENGINE
 * Features:
 *  1. Mouse Ambient Glow
 *  2. Global STOMP / WebSocket Notifications (Shows on ALL pages)
 *  3. Global Bilingual Translation Engine (English & Bangla)
 *  4. Browser Console OTP Logger
 * 
 * Created by: Shamsil Arefin & Nafisa Tasnim Sohel | CSE UIU
 */

(function () {
    'use strict';

    // =========================================================================
    // 1. MOUSE AMBIENT GLOW
    // =========================================================================
    document.addEventListener('DOMContentLoaded', () => {
        if (!document.querySelector('.mouse-glow')) {
            const glow = document.createElement('div');
            glow.className = 'mouse-glow';
            document.body.appendChild(glow);

            let mouseX = window.innerWidth / 2;
            let mouseY = window.innerHeight / 2;
            let glowX = mouseX;
            let glowY = mouseY;

            window.addEventListener('mousemove', (e) => {
                mouseX = e.clientX;
                mouseY = e.clientY;
            });

            function animateGlow() {
                const dx = mouseX - glowX;
                const dy = mouseY - glowY;
                glowX += dx * 0.12;
                glowY += dy * 0.12;
                glow.style.left = glowX + 'px';
                glow.style.top = glowY + 'px';
                requestAnimationFrame(animateGlow);
            }
            animateGlow();
        }

        // Initialize Global Notifications
        initGlobalNotifications();

        // Initialize Global Bilingual Engine
        initBilingualEngine();
    });

    // =========================================================================
    // 2. GLOBAL NOTIFICATION SYSTEM (SHOWS ON ALL PAGES)
    // =========================================================================
    function initGlobalNotifications() {
        // Ensure notification container exists on this page
        let container = document.getElementById('mukti-notification-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'mukti-notification-container';
            container.className = 'notification-container';
            document.body.appendChild(container);
        }

        // Check if SockJS and Stomp are loaded; if not, dynamically load them
        loadScript('https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js', () => {
            loadScript('https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js', () => {
                connectWebSocket();
            });
        });
    }

    function loadScript(src, callback) {
        if (document.querySelector(`script[src="${src}"]`)) {
            if (callback) callback();
            return;
        }
        const s = document.createElement('script');
        s.src = src;
        s.onload = () => { if (callback) callback(); };
        s.onerror = () => { console.warn('Could not load script: ' + src); };
        document.head.appendChild(s);
    }

    let stompClient = null;

    function connectWebSocket() {
        try {
            if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') return;

            const socket = new SockJS('/ws-stomp');
            stompClient = Stomp.over(socket);
            stompClient.debug = null; // Clean console

            stompClient.connect({}, function (frame) {
                // 1. Subscribe to public/global broadcast topic (Logins, new donations, announcements)
                stompClient.subscribe('/topic/notifications', function (notification) {
                    try {
                        const data = JSON.parse(notification.body);
                        showMuktiNotification(data);
                    } catch (e) {
                        console.error('Error parsing notification', e);
                    }
                });

                // 2. Check if user is logged in to subscribe to private topic
                const userPhoneMeta = document.querySelector('meta[name="user-phone"]');
                const userPhone = userPhoneMeta ? userPhoneMeta.getAttribute('content') : (window.MUKTI_USER_PHONE || '');

                if (userPhone && userPhone.trim() !== '') {
                    stompClient.subscribe('/topic/notifications/' + userPhone.trim(), function (notification) {
                        try {
                            const data = JSON.parse(notification.body);
                            showMuktiNotification(data);
                        } catch (e) {
                            console.error('Error parsing private notification', e);
                        }
                    });
                }
            }, function (error) {
                // Silently attempt reconnect after 10s if backend restarts
                setTimeout(connectWebSocket, 10000);
            });
        } catch (e) {
            console.warn('WebSocket connection not initialized', e);
        }
    }

    window.showMuktiNotification = function (data) {
        if (!data || !data.message) return;

        let container = document.getElementById('mukti-notification-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'mukti-notification-container';
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        const type = data.type || 'info';
        toast.className = `mukti-toast ${type}`;

        let icon = 'fa-bell';
        let defaultTitleEn = 'Notification';
        let defaultTitleBn = 'বিজ্ঞপ্তি';

        switch (type) {
            case 'donation':
                icon = 'fa-gift';
                defaultTitleEn = 'New Donation Listed';
                defaultTitleBn = 'নতুন দান যুক্ত হয়েছে';
                break;
            case 'points':
                icon = 'fa-trophy';
                defaultTitleEn = 'Points Earned!';
                defaultTitleBn = 'পয়েন্ট অর্জিত হয়েছে!';
                break;
            case 'request':
                icon = 'fa-hand-holding-heart';
                defaultTitleEn = 'Community Food Request';
                defaultTitleBn = 'খাবারের আবেদন';
                break;
            case 'message':
                icon = 'fa-comment-alt';
                defaultTitleEn = 'New Message';
                defaultTitleBn = 'নতুন বার্তা';
                break;
            case 'login':
                icon = 'fa-user-check';
                defaultTitleEn = 'Community Activity';
                defaultTitleBn = 'কমিউনিটি আপডেট';
                break;
            case 'update':
                icon = 'fa-sync-alt';
                defaultTitleEn = 'Status Update';
                defaultTitleBn = 'স্ট্যাটাস আপডেট';
                break;
            case 'cancel':
                icon = 'fa-times-circle';
                defaultTitleEn = 'Notice';
                defaultTitleBn = 'বিজ্ঞপ্তি';
                break;
        }

        const isBn = (localStorage.getItem('mukti_lang') || 'en') === 'bn';
        const title = isBn ? defaultTitleBn : defaultTitleEn;

        toast.innerHTML = `
            <div class="toast-icon"><i class="fas ${icon}"></i></div>
            <div class="toast-content">
                <div class="toast-title">
                    <span>${title}</span>
                    <button class="toast-close" title="Close"><i class="fas fa-times"></i></button>
                </div>
                <div class="toast-message">${escapeHtml(data.message)}</div>
            </div>
        `;

        container.appendChild(toast);

        // Click to close
        const closeBtn = toast.querySelector('.toast-close');
        if (closeBtn) {
            closeBtn.onclick = (e) => {
                e.stopPropagation();
                removeToast(toast);
            };
        }

        // Click to navigate based on type
        toast.onclick = () => {
            removeToast(toast);
            if (type === 'message' && window.location.pathname !== '/chat') window.location.href = '/chat';
            if (type === 'donation' && window.location.pathname !== '/food/receive-page') window.location.href = '/food/receive-page';
            if (type === 'request' && window.location.pathname !== '/food/donate') window.location.href = '/food/donate';
            if (type === 'points' && window.location.pathname !== '/food/leaderboard') window.location.href = '/food/leaderboard';
        };

        // Auto remove after 6 seconds
        setTimeout(() => {
            removeToast(toast);
        }, 6000);
    };

    function removeToast(toast) {
        if (!toast || toast.classList.contains('removing')) return;
        toast.classList.add('removing');
        toast.style.animation = 'toastSlideOut 0.4s forwards';
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 400);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"']/g, function (m) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#039;'
            }[m];
        });
    }

    // =========================================================================
    // 3. CONSOLE OTP LOGGER (FOR PHONE & EMAIL VERIFICATION)
    // =========================================================================
    window.logMuktiOtp = function (otp, emailOrPhone) {
        if (!otp) return;
        const bannerStyle = "background: #120e0a; color: #f59e0b; font-size: 14px; font-weight: bold; padding: 6px 12px; border-radius: 6px; border: 1px solid #f59e0b;";
        const otpStyle = "background: #10b981; color: #000000; font-size: 18px; font-weight: 900; padding: 6px 16px; border-radius: 6px; margin-left: 6px; letter-spacing: 2px;";
        const subStyle = "color: #94a3b8; font-size: 12px; margin-top: 4px;";

        console.log("%c🔑 [MUKTI APP OTP VERIFICATION CODE]%c" + otp, bannerStyle, otpStyle);
        if (emailOrPhone) {
            console.log("%cTarget Account: " + emailOrPhone + " | Valid for 5 minutes.", subStyle);
        }
    };

    // =========================================================================
    // 4. BILINGUAL TRANSLATION ENGINE (ENGLISH & BANGLA)
    // =========================================================================
    const I18N_DICT = {
        // Navigation
        "Home": "নীড়পাতা",
        "Features": "বৈশিষ্ট্য",
        "How It Works": "কীভাবে কাজ করে",
        "Leaderboard": "সেরা দাতা",
        "Login": "লগইন",
        "Register": "নিবন্ধন",
        "Get Started": "শুরু করুন",
        "Sign In": "সাইন ইন",
        "Sign Out": "লগআউট",
        "Dashboard": "ড্যাশবোর্ড",
        "Donate Food": "খাবার দান করুন",
        "Find Food": "খাবার খুঁজুন",
        "Receive Food": "খাবার গ্রহণ করুন",
        "Request Food": "খাবারের আবেদন",
        "History": "ইতিহাস",
        "Chat": "মেসেজ / চ্যাট",
        "Profile": "প্রোফাইল",
        "Back": "ফিরে যান",

        // Hero & Main
        "Fight Hunger, Reduce Waste": "ক্ষুধা মুক্ত সমাজ, অপচয়হীন জীবন",
        "Connecting surplus food with those in need.": "উদ্বৃত্ত খাবার পৌঁছে যাক সুবিধাবঞ্চিত মানুষের কাছে।",
        "Together, we eliminate waste and fight hunger for a sustainable future.": "আসুন একসাথে খাদ্যের অপচয় রোধ করি এবং একটি সুন্দর ক্ষুধামুক্ত ভবিষ্যৎ গড়ি।",
        "Join the Movement": "আমাদের সাথে যুক্ত হন",
        "Start Donating Now": "এখনই দান শুরু করুন",
        "Request Food Support": "খাদ্য সহায়তা চান",
        "Food Saved": "খাবার সংরক্ষিত",
        "Meals Shared": "বিতরণকৃত খাবার",
        "Active Donors": "সক্রিয় দাতা",
        "Lives Impacted": "উপকৃত পরিবার",

        // Campaigns
        "Sacred Giving & Seasonal Drives": "পবিত্র রমজান ও কোরবানি বিশেষ উদ্যোগ",
        "Holy Ramadan Drive": "পবিত্র মাহে রমজান স্পেশাল ড্রাইভ",
        "Donate Iftar & Sehri": "ইফতার ও সেহরি বিতরণ করুন",
        "Donate Iftar & Sehri (+5 Bonus Points)": "ইফতার ও সেহরি দান (+৫ বোনাস পয়েন্ট)",
        "Earn 15 Points (10 Base + 5 Bonus!)": "১৫ পয়েন্ট অর্জন করুন (১০ মূল + ৫ বোনাস!)",
        "Qurbani Meat Drive": "পবিত্র কোরবানি মাংস বিতরণ ড্রাইভ",
        "Donate Qurbani Meat": "কোরবানির মাংস দান করুন",
        "Donate Qurbani Meat (+5 Bonus Points)": "কোরবানির মাংস দান (+৫ বোনাস পয়েন্ট)",
        "Warmth & Dignity Drive": "শীতবস্ত্র, কম্বল ও পোশাক সহায়তা (আলাদা সেকশন)",
        "Donate Blankets & Dresses": "কম্বল ও পোশাক দান করুন",
        "Donate Blankets & Clothes (Earn 30 Points!)": "কম্বল ও পোশাক দান (৩০ পয়েন্ট বোনাস!)",
        "Earn 30 Points Bonus!": "৩০ বোনাস পয়েন্ট অর্জন করুন!",
        "Special 30 Points Reward": "বিশেষ ৩০ পয়েন্ট উপহার",

        // Creators / UIU CSE
        "Created & Developed By": "অ্যাপ তৈরি ও ডিজাইন করেছেন",
        "Department of Computer Science & Engineering": "কম্পিউটার বিজ্ঞান ও প্রকৌশল বিভাগ",
        "United International University (UIU)": "ইউনাইটেড ইন্টারন্যাশনাল ইউনিভার্সিটি (UIU)",
        "Lead Developer & System Architect": "প্রধান ডেভেলপার ও সিস্টেম আর্কিটেক্ট",
        "Lead Designer & Full-Stack Developer": "প্রধান ডিজাইনার ও ফুল-স্ট্যাক ডেভেলপার",

        // Form Fields & Actions
        "Food Name": "খাবারের নাম",
        "Item Name": "আইটেমের নাম",
        "Description (Optional)": "বিবরণ (ঐচ্ছিক)",
        "Quantity": "পরিমাণ / প্যাকেট",
        "Servings": "জনের খাবার",
        "Location": "এলাকা / ঠিকানা",
        "Area / Location": "এলাকা নির্বাচন",
        "Cook Time": "রান্নার সময়",
        "Expire Time": "মেয়াদ উত্তীর্ণের সময়",
        "Phone (Optional)": "যোগাযোগ নম্বর",
        "Post Donation": "দান প্রকাশ করুন",
        "Receive": "গ্রহণ করুন",
        "Search by location...": "এলাকা বা লোকেশন দিয়ে খুঁজুন...",
        "All Areas": "সকল এলাকা",
        "Available": "উপলব্ধ",
        "Processing": "প্রক্রিয়াধীন",
        "Received": "সম্পন্ন হয়েছে",
        "Cancelled": "বাতিল",
        "Status": "অবস্থা",
        "Points": "পয়েন্ট",
        "User Profile": "ব্যবহারকারী প্রোফাইল",
        "Language Settings": "ভাষা সেটিংস",
        "Select Website Language": "ওয়েবসাইটের ভাষা নির্বাচন করুন",
        "Switch site language": "ওয়েবসাইটের ভাষা পরিবর্তন করুন",
        "Edit details & preferences": "তথ্য ও সেটিংস পরিবর্তন করুন",
        "Phone / Account ID": "মোবাইল / অ্যাকাউন্ট আইডি",
        "Total Points": "মোট পয়েন্ট",
        "Full Name": "পূর্ণ নাম",
        "Email Address": "ইমেইল ঠিকানা",
        "City / Division": "শহর / বিভাগ",
        "Area / Thana": "এলাকা / থানা",
        "Detailed Address": "বিস্তারিত ঠিকানা",
        "Save Profile": "প্রোফাইল সংরক্ষণ",
        "Cancel": "বাতিল",
        "Logout": "লগআউট",
        "Logout from Mukti App": "মুক্তি অ্যাপ থেকে লগআউট",
        "Available Food Donations Nearby": "কাছাকাছি উপলব্ধ খাদ্য তালিকা",
        "Fresh meals & essential supplies ready for sharing": "অপচয়মুক্ত খাবার ও সহায়তার সরাসরি তালিকা",
        "Browse available surplus food and community drives": "উদ্বৃত্ত খাবার ও কমিউনিটি অনুদানসমূহ দেখুন",
        "Donor details hidden &bull; Login to view": "দাতার তথ্য সুরক্ষিত &bull; দেখতে লগইন করুন",
        "Donor details hidden • Login to view": "দাতার তথ্য সুরক্ষিত • দেখতে লগইন করুন",
        "Donor Details: Login required to view": "দাতার তথ্য: দেখতে লগইন করুন",
        "Login to Receive": "গ্রহণ করতে লগইন করুন",
        "View All Food Donations": "সকল খাদ্য তালিকা দেখুন"
    };

    function initBilingualEngine() {
        const currentLang = localStorage.getItem('mukti_lang') || 'en';
        document.documentElement.lang = currentLang;
        document.body.setAttribute('data-lang', currentLang);

        // Inject language toggle widget in all navbars if not already present
        injectLanguageSwitcher(currentLang);

        // Apply translations
        applyLanguage(currentLang);
    }

    function injectLanguageSwitcher(currentLang) {
        const navbars = document.querySelectorAll('.navbar, .header-nav, header, .chat-header');
        navbars.forEach(nav => {
            if (nav.querySelector('.lang-switcher')) return;

            const switcher = document.createElement('div');
            switcher.className = 'lang-switcher';
            switcher.innerHTML = `
                <button type="button" class="lang-btn ${currentLang === 'en' ? 'active' : ''}" data-target-lang="en">EN</button>
                <button type="button" class="lang-btn ${currentLang === 'bn' ? 'active' : ''}" data-target-lang="bn">বাংলা</button>
            `;

            switcher.querySelectorAll('.lang-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    const targetLang = btn.getAttribute('data-target-lang');
                    setMuktiLanguage(targetLang);
                });
            });

            // Find best place to append in navbar
            const navLinks = nav.querySelector('.nav-links') || 
                             nav.querySelector('.user-actions') || 
                             nav.querySelector('.header-right-actions') || 
                             nav.querySelector('.header-nav-group');
            if (navLinks) {
                // If there is a Profile, Logout, or CTA button, place switcher right before it
                const targetBtn = navLinks.querySelector('.nav-profile-btn, .logout-btn, .cta-btn, .login-btn');
                if (targetBtn) {
                    navLinks.insertBefore(switcher, targetBtn);
                } else {
                    navLinks.appendChild(switcher);
                }
            } else {
                // Safe append at end of navbar (right side in flex layout), never before logo!
                nav.appendChild(switcher);
            }
        });
    }

    window.setMuktiLanguage = function (lang) {
        localStorage.setItem('mukti_lang', lang);
        document.documentElement.lang = lang;
        document.body.setAttribute('data-lang', lang);

        document.querySelectorAll('.lang-btn').forEach(btn => {
            if (btn.getAttribute('data-target-lang') === lang) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        applyLanguage(lang);
    };

    function applyLanguage(lang) {
        // 1. Elements with explicit data-en and data-bn
        document.querySelectorAll('[data-en][data-bn]').forEach(el => {
            const val = lang === 'bn' ? el.getAttribute('data-bn') : el.getAttribute('data-en');
            if (val) el.textContent = val;
        });

        // 2. Elements with data-placeholder-en and data-placeholder-bn
        document.querySelectorAll('[data-placeholder-en][data-placeholder-bn]').forEach(el => {
            const val = lang === 'bn' ? el.getAttribute('data-placeholder-bn') : el.getAttribute('data-placeholder-en');
            if (val) el.setAttribute('placeholder', val);
        });

        // 3. Fallback dictionary replacement for common navigation and headers
        if (lang === 'bn') {
            translateMatchingElements(document.body);
        } else {
            // Restore English if element has data-original-text
            document.querySelectorAll('[data-original-en]').forEach(el => {
                el.textContent = el.getAttribute('data-original-en');
            });
        }
    }

    function translateMatchingElements(root) {
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
        let node;
        while (node = walker.nextNode()) {
            const trimmed = node.nodeValue.trim();
            if (trimmed && I18N_DICT[trimmed]) {
                const parent = node.parentElement;
                if (parent && !parent.hasAttribute('data-original-en') && parent.tagName !== 'SCRIPT' && parent.tagName !== 'STYLE') {
                    parent.setAttribute('data-original-en', trimmed);
                    node.nodeValue = node.nodeValue.replace(trimmed, I18N_DICT[trimmed]);
                }
            }
        }
    }

})();
