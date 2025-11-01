// Product data object
const products = {
    "trump-flag": {
        title: "Trump 2024 Flag (Pick one from two designs)",
        searchTerm: "Trump 2024 flag double sided Gamexcel",
        image1: "promo-img/flag1.jpg",
        image2: "promo-img/flag2.jpg"
    },
    "trump-hat": {
        title: "Trump Hat 2024",
        searchTerm: "Trump  2024 Hat Gamexel",
        image1: "promo-img/hat1.jpg",
        image2: "promo-img/hat2.jpg"
    },
    "trump-yard-sign": {
        title: "Trump Yard Sign 2024",
        searchTerm: "Trump yard sign gamexcel",
        image1: "promo-img/trump-sign1.jpg",
        image2: "promo-img/trump-sign2.jpg"
    }
};

// Function to get the hash fragment from the URL
function getHashFragment() {
    return window.location.hash.substring(1);
}

// Function to load product content based on the hash fragment
function loadProductFromHash() {
    const productKey = getHashFragment();
    const productLinksDiv = document.getElementById('product-links');

    if (productKey && products[productKey]) {
        const product = products[productKey];
        document.getElementById('product-title').textContent = product.title;
        document.getElementById('search-term').textContent = product.searchTerm;
        document.getElementById('image1').src = product.image1;
        document.getElementById('image2').src = product.image2;
        productLinksDiv.style.display = 'none';  // Hide links when a product is displayed
    } else if (!productKey) {
        productLinksDiv.style.display = 'block';
        displayProductLinks();
    } else {
        document.getElementById('product-title').textContent = "Product Not Found";
        document.getElementById('search-term').textContent = "Invalid product key in URL.";
        document.getElementById('image1').src = "promo-img/error1.jpg";
        document.getElementById('image2').src = "promo-img/error2.jpg";
        productLinksDiv.style.display = 'none';
    }
}

// Function to display product links at the top if no hash is present
function displayProductLinks() {
    const productLinksDiv = document.getElementById('product-links');
    let linksHTML = '<h2>Select a Product</h2><ul>';

    for (const key in products) {
        if (products.hasOwnProperty(key)) {
            linksHTML += `<li><a href="#${key}">${products[key].title}</a></li>`;
        }
    }

    linksHTML += '</ul>';
    productLinksDiv.innerHTML = linksHTML;
}

// Load the correct product content when the page loads
window.onload = function() {
    loadProductFromHash();
};

// Optionally, update the content if the hash changes (e.g., user changes the URL)
window.onhashchange = function() {
    loadProductFromHash();
};
