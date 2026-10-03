// 삭제 기능
const deleteButton = document.getElementById('delete-btn');

if (deleteButton) {
    deleteButton.addEventListener('click', event => {
        let id = document.getElementById('article-id').value;
        function success() {
            alert('삭제가 완료되었습니다.');
            location.replace('/articles');
        }

        function fail() {
            alert('삭제 실패했습니다.');
            location.replace('/articles');
        }

        httpRequest('DELETE',`/api/articles/${id}`, null, success, fail);
    });
}

// 수정 기능
const modifyButton = document.getElementById('modify-btn');

if (modifyButton) {
    modifyButton.addEventListener('click', event => {
        let params = new URLSearchParams(location.search);
        let id = params.get('id');

        body = JSON.stringify({
            title: document.getElementById('title').value,
            content: document.getElementById('content').value,
            category: 'DAILY'
        })

        function success() {
            alert('수정 완료되었습니다.');
            location.replace(`/articles/${id}`);
        }

        function fail() {
            alert('수정 실패했습니다.');
            location.replace(`/articles/${id}`);
        }

        httpRequest('PUT',`/api/articles/${id}`, body, success, fail);
    });
}

// 생성 기능
const createButton = document.getElementById('create-btn');

if (createButton) {
    createButton.addEventListener('click', event => {
        body = JSON.stringify({
            title: document.getElementById('title').value,
            content: document.getElementById('content').value,
            category: 'DAILY'
        });
        function success() {
            alert('등록 완료되었습니다.');
            location.replace('/articles');
        };
        function fail() {
            alert('등록 실패했습니다.');
            location.replace('/articles');
        };

        httpRequest('POST','/api/articles', body, success, fail)
    });
}

// 쿠키 값을 읽는 함수
function getCookie(name) {
    const found = document.cookie.split('; ').find(part => part.startsWith(name + '='));
    return found ? decodeURIComponent(found.substring(name.length + 1)) : null;
}

// CSRF 토큰: XSRF-TOKEN 쿠키 값을 그대로 X-XSRF-TOKEN 헤더에 담는다. 쿠키가 없으면 먼저 발급받는다.
async function csrfToken() {
    let token = getCookie('XSRF-TOKEN');
    if (!token) {
        await fetch('/api/csrf');
        token = getCookie('XSRF-TOKEN');
    }
    return token;
}

// HTTP 요청을 보내는 함수
// 로그인 상태는 세션 쿠키(BLOG_SESSION)로 전달되므로 토큰을 따로 붙이지 않는다.
async function httpRequest(method, url, body, success, fail) {
    const headers = { 'Content-Type': 'application/json' };
    if (method !== 'GET') {
        headers['X-XSRF-TOKEN'] = await csrfToken();
    }
    fetch(url, {
        method: method,
        headers: headers,
        body: body,
    }).then(response => {
        if (response.ok) {
            return success();
        }
        // 로그인이 필요하면 accounts 로그인으로 이동한다.
        if (response.status === 401) {
            location.href = '/oauth2/authorization/accounts';
            return;
        }
        return fail();
    });
}
